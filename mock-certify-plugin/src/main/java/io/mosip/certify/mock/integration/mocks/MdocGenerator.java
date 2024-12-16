package io.mosip.certify.mock.integration.mocks;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator;
import com.android.identity.mdoc.util.MdocUtil;
import com.android.identity.util.Timestamp;
import io.mosip.certify.util.*;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class MdocGenerator {

    public static final String NAMESPACE = "org.iso.18013.5.1";
    public static final String DOCTYPE = NAMESPACE + ".mDL";
    public static final String DIGEST_ALGORITHM = "SHA-256";
    public static final String ECDSA_ALGORITHM = "SHA256withECDSA";
    public static final long SEED = 42L;
    public static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public String generate(Map<String, Object> data, String holderId, String issuerKeyAndCertificate) throws Exception {
        PKCS12Reader pkcs12Reader = new PKCS12Reader();
        KeyPairAndCertificate issuerDetails = pkcs12Reader.extract(issuerKeyAndCertificate);

        if (issuerDetails.keyPair() == null) {
            throw new RuntimeException("Unable to load Crypto details");
        }

        JwkToKeyConverter jwkToKeyConverter = new JwkToKeyConverter();
        PublicKey devicePublicKey = jwkToKeyConverter.convertToPublicKey(holderId.replace("did:jwk:", ""));
        KeyPair issuerKeypair = issuerDetails.keyPair();

        LocalDate issueDate = LocalDate.now();
        String formattedIssueDate = issueDate.format(FULL_DATE_FORMATTER);
        LocalDate expiryDate = issueDate.plusYears(5);
        String formattedExpiryDate = expiryDate.format(FULL_DATE_FORMATTER);

        NameSpacedData.Builder nameSpacedDataBuilder = new NameSpacedData.Builder();
        nameSpacedDataBuilder.putEntryString(NAMESPACE, "issue_date", formattedIssueDate);
        nameSpacedDataBuilder.putEntryString(NAMESPACE, "expiry_date", formattedExpiryDate);

        Map<String, String> drivingPrivileges = (Map<String, String>) data.get("driving_privileges");
        drivingPrivileges.put("issue_date", formattedIssueDate);
        drivingPrivileges.put("expiry_date", formattedExpiryDate);

        for (String key : data.keySet()) {
            nameSpacedDataBuilder.putEntryString(NAMESPACE, key, data.get(key).toString());
        }

        NameSpacedData nameSpacedData = nameSpacedDataBuilder.build();
        Map<String, List<byte[]>> generatedIssuerNameSpaces = MdocUtil.generateIssuerNameSpaces(nameSpacedData, new Random(SEED), 16);
        Map<Long, byte[]> calculateDigestsForNameSpace = MdocUtil.calculateDigestsForNameSpace(NAMESPACE, generatedIssuerNameSpaces, DIGEST_ALGORITHM);

        MobileSecurityObjectGenerator mobileSecurityObjectGenerator = new MobileSecurityObjectGenerator(DIGEST_ALGORITHM, DOCTYPE, devicePublicKey);
        mobileSecurityObjectGenerator.addDigestIdsForNamespace(NAMESPACE, calculateDigestsForNameSpace);

        Timestamp currentTimestamp = Timestamp.now();
        Timestamp validUntil = Timestamp.ofEpochMilli(addYearsToDate(currentTimestamp.toEpochMilli(), 2));
        mobileSecurityObjectGenerator.setValidityInfo(currentTimestamp, currentTimestamp, validUntil, null);

        byte[] mso = mobileSecurityObjectGenerator.generate();

        DataItem coseSign1Sign = Util.coseSign1Sign(
                issuerKeypair.getPrivate(),
                ECDSA_ALGORITHM,
                Util.cborEncode(Util.cborBuildTaggedByteString(mso)),
                null,
                Collections.singletonList(issuerDetails.certificate())
        );

        return construct(generatedIssuerNameSpaces, coseSign1Sign);
    }

    private String construct(Map<String, List<byte[]>> nameSpaces, DataItem issuerAuth) throws CborException {
        MDoc mDoc = new MDoc(DOCTYPE, new IssuerSigned(nameSpaces, issuerAuth));
        byte[] cbor = mDoc.toCBOR();
        return Base64.getUrlEncoder().encodeToString(cbor);
    }

    private long addYearsToDate(long dateInEpochMillis, int years) {
        Instant instant = Instant.ofEpochMilli(dateInEpochMillis);
        Instant futureInstant = instant.plus(years * 365L, ChronoUnit.DAYS);
        return futureInstant.toEpochMilli();
    }
}


class MDoc {
    private final String docType;
    private final IssuerSigned issuerSigned;

    public MDoc(String docType, IssuerSigned issuerSigned) {
        this.docType = docType;
        this.issuerSigned = issuerSigned;
    }

    public byte[] toCBOR() throws CborException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CborEncoder cborEncoder = new CborEncoder(byteArrayOutputStream);
        cborEncoder.encode(
                new CborBuilder().addMap()
                        .put("docType", docType)
                        .put(CBORConverter.toDataItem("issuerSigned"), CBORConverter.toDataItem(issuerSigned.toMap()))
                        .end()
                        .build()
        );
        return byteArrayOutputStream.toByteArray();
    }
}

class IssuerSigned {
    private final Map<String, List<byte[]>> nameSpaces;
    private final DataItem issuerAuth;

    public IssuerSigned(Map<String, List<byte[]>> nameSpaces, DataItem issuerAuth) {
        this.nameSpaces = nameSpaces;
        this.issuerAuth = issuerAuth;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("nameSpaces", CBORConverter.toDataItem(nameSpaces));
        map.put("issuerAuth", issuerAuth);
        return map;
    }
}

