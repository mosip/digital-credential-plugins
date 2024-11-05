package io.mosip.certify.mock.integration.mocks

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.util.Timestamp
import io.mosip.certify.util.CBORConverter
import io.mosip.certify.util.JwkToKeyConverter
import io.mosip.certify.util.KeyPairAndCertificate
import io.mosip.certify.util.PKCS12Reader
import io.mosip.certify.util.UUIDGenerator
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*


class MdocGenerator {
    companion object {
        const val NAMESPACE: String = "org.iso.18013.5.1"
        const val DOCTYPE: String = "$NAMESPACE.mDL"
        const val DIGEST_ALGORITHM = "SHA-256"
        const val ECDSA_ALGORITHM = "SHA256withECDSA"
        const val SEED = 42L
        val FULL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    fun generate(
        data: MutableMap<String, out Any>,
        holderId: String,
        issuerKeyAndCertificate: String
    ): String? {
        val issuerDetails: KeyPairAndCertificate = PKCS12Reader().extract(issuerKeyAndCertificate)

        if (issuerDetails.keyPair == null) {
            throw RuntimeException("Unable to load Crypto details")
        }
        val devicePublicKey = JwkToKeyConverter().convertToPublicKey(holderId.replace("did:jwk:", ""))
        val issuerKeypair = issuerDetails.keyPair


        val issueDate: LocalDate = LocalDate.now()
        val formattedIssueDate: String = issueDate.format(FULL_DATE_FORMATTER)
        val expiryDate = issueDate.plusYears(5)
        val formattedExpiryDate = expiryDate.format(FULL_DATE_FORMATTER)
        val nameSpacedDataBuilder: NameSpacedData.Builder = NameSpacedData.Builder()
        //Validity of document is assigned here
        nameSpacedDataBuilder.putEntryString(NAMESPACE, "issue_date", (formattedIssueDate))
        nameSpacedDataBuilder.putEntryString(NAMESPACE, "expiry_date", (formattedExpiryDate))
        (data.get("driving_privileges") as HashMap<String, String>)["issue_date"] = formattedIssueDate
        (data.get("driving_privileges") as MutableMap<String, String>)["expiry_date"] = formattedExpiryDate
        data.keys.forEach { key ->
            nameSpacedDataBuilder.putEntryString(NAMESPACE, key, data[key].toString())
        }


        val nameSpacedData: NameSpacedData =
            nameSpacedDataBuilder
                .build()
        val generatedIssuerNameSpaces: MutableMap<String, MutableList<ByteArray>> =
            MdocUtil.generateIssuerNameSpaces(nameSpacedData, Random(SEED), 16)
        val calculateDigestsForNameSpace =
            MdocUtil.calculateDigestsForNameSpace(NAMESPACE, generatedIssuerNameSpaces, DIGEST_ALGORITHM)

        val mobileSecurityObjectGenerator = MobileSecurityObjectGenerator(DIGEST_ALGORITHM, DOCTYPE, devicePublicKey)
        mobileSecurityObjectGenerator.addDigestIdsForNamespace(NAMESPACE, calculateDigestsForNameSpace)
        //Validity of MSO & its signature is assigned here
        val currentTimestamp = Timestamp.now()
        val validUntil = Timestamp.ofEpochMilli(addYearsToDate(currentTimestamp.toEpochMilli(), 2))
        mobileSecurityObjectGenerator.setValidityInfo(
            currentTimestamp,
            currentTimestamp,
            validUntil,
            null
        )
        val mso: ByteArray = mobileSecurityObjectGenerator.generate()

        val coseSign1Sign: DataItem = Util.coseSign1Sign(
            issuerKeypair.private,
            ECDSA_ALGORITHM,
            Util.cborEncode(Util.cborBuildTaggedByteString(mso)),
            null,
            listOf(issuerDetails.certificate)
        )

        return construct(generatedIssuerNameSpaces, coseSign1Sign)
    }

    private fun construct(nameSpaces: MutableMap<String, MutableList<ByteArray>>, issuerAuth: DataItem): String? {
        val mDoc = MDoc(DOCTYPE, IssuerSigned(nameSpaces, issuerAuth))
        val cbor = mDoc.toCBOR()
        return Base64.getUrlEncoder().encodeToString(cbor)
    }

    private fun addYearsToDate(dateInEpochMillis: Long, years: Int): Long {
        val instant: Instant = Instant.ofEpochMilli(dateInEpochMillis)
        val futureInstant: Instant = instant.plus((years*365).toLong(), ChronoUnit.DAYS)

        return futureInstant.toEpochMilli()
    }
}

data class MDoc(val docType: String, val issuerSigned: IssuerSigned) {
    fun toCBOR(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        CborEncoder(byteArrayOutputStream).encode(
            CborBuilder().addMap()
                .put("docType", docType)
                .put("id", UUIDGenerator().generate())
                .put(CBORConverter.toDataItem("issuerSigned"), CBORConverter.toDataItem(issuerSigned.toMap()))
                .end()
                .build()
        )
        return byteArrayOutputStream.toByteArray()

    }
}

data class IssuerSigned(val nameSpaces: MutableMap<String, MutableList<ByteArray>>, val issuerAuth: DataItem) {
    fun toMap(): Map<String, Any> {
        return buildMap {
            put("nameSpaces", CBORConverter.toDataItem(nameSpaces))
            put("issuerAuth", issuerAuth)
        }
    }
}
