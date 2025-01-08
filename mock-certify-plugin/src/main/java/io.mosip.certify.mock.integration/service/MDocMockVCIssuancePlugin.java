package io.mosip.certify.mock.integration.service;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.api.spi.VCIssuancePlugin;
import io.mosip.certify.api.util.ErrorConstants;
import io.mosip.certify.constants.VCFormats;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.certify.mock.integration.mocks.MdocGenerator;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@ConditionalOnProperty(value = "mosip.certify.integration.vci-plugin", havingValue = "MDocMockVCIssuancePlugin")
@Component
@Slf4j
public class MDocMockVCIssuancePlugin implements VCIssuancePlugin {
    private static final String AES_CIPHER_FAILED = "aes_cipher_failed";
    private static final String NO_UNIQUE_ALIAS = "no_unique_alias";
    private static final String USERINFO_CACHE = "userinfo";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private KeyStore keyStore;

    @Autowired
    private KeymanagerDBHelper dbHelper;

    @Value("${mosip.certify.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("${mosip.certify.cache.security.algorithm-name}")
    private String aesECBTransformation;

    @Value("${mosip.certify.cache.secure.individual-id}")
    private boolean secureIndividualId;

    @Value("${mosip.certify.cache.store.individual-id}")
    private boolean storeIndividualId;

    @Value("${mosip.certify.mock.vciplugin.mdoc.issuer-key-cert:empty}")
    private String issuerKeyAndCertificate = null;

    private static final String ACCESS_TOKEN_HASH = "accessTokenHash";

    public static final String CERTIFY_SERVICE_APP_ID = "CERTIFY_SERVICE";

    @Override
    public VCResult<JsonLDObject> getVerifiableCredentialWithLinkedDataProof(VCRequestDto vcRequestDto, String holderId, Map<String, Object> identityDetails) throws VCIExchangeException {
        log.error("not implemented the format {}", vcRequestDto);
        throw new VCIExchangeException(ErrorConstants.NOT_IMPLEMENTED);
    }

    @Override
    public VCResult<String> getVerifiableCredential(VCRequestDto vcRequestDto, String holderId, Map<String, Object> identityDetails) throws VCIExchangeException {
        String accessTokenHash = identityDetails.get(ACCESS_TOKEN_HASH).toString();
        String documentNumber;
        try {
            documentNumber = getIndividualId(getUserInfoTransaction(accessTokenHash));
        } catch (Exception e) {
            log.error("Error getting documentNumber", e);
            throw new VCIExchangeException(ErrorConstants.VCI_EXCHANGE_FAILED);
        }

        if(vcRequestDto.getFormat().equals(VCFormats.MSO_MDOC)){
            VCResult<String> vcResult = new VCResult<>();
            String mdocVc;
            try {
                mdocVc = new MdocGenerator().generate(mockDataForMsoMdoc(documentNumber),holderId, issuerKeyAndCertificate);
            } catch (Exception e) {
                log.error("Exception on mdoc creation", e);
                throw new VCIExchangeException(ErrorConstants.VCI_EXCHANGE_FAILED);
            }
            vcResult.setCredential(mdocVc);
            vcResult.setFormat(VCFormats.MSO_MDOC);
            return  vcResult;
        }
        log.error("not implemented the format {}", vcRequestDto);
        throw new VCIExchangeException(ErrorConstants.NOT_IMPLEMENTED);
    }

    private Map<String, Object> mockDataForMsoMdoc(String documentNumber) {
        Map<String, Object> data = new HashMap<>();
        log.info("Setting up the data for mDoc");
        data.put("family_name","Agatha");
        data.put("given_name","Joseph");
        data.put("birth_date", "1994-11-06");
        data.put("issuing_country", "IN");
        data.put("document_number", documentNumber);
        data.put("driving_privileges",new HashMap<>(){{
            put("vehicle_category_code","A");
        }});
        return data;
    }

    /**
     * TODO: This function getIndividualId is duplicated with Other VCIPlugin class and can be moved to commons
     */
    protected String getIndividualId(OIDCTransaction transaction) {
        if(!storeIndividualId)
            return null;
        return secureIndividualId ? decryptIndividualId(transaction.getIndividualId()) : transaction.getIndividualId();
    }

    private String decryptIndividualId(String encryptedIndividualId) {
        try {
            Cipher cipher = Cipher.getInstance(aesECBTransformation);
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encryptedIndividualId);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKeyFromHSM());
            return new String(cipher.doFinal(decodedBytes, 0, decodedBytes.length));
        } catch(Exception e) {
            log.error("Error Cipher Operations of provided secret data.", e);
            throw new CertifyException(AES_CIPHER_FAILED);
        }
    }

    private OIDCTransaction getUserInfoTransaction(String accessTokenHash) {
        return cacheManager.getCache(USERINFO_CACHE).get(accessTokenHash, OIDCTransaction.class);
    }

    private Key getSecretKeyFromHSM() {
        String keyAlias = getKeyAlias(CERTIFY_SERVICE_APP_ID, cacheSecretKeyRefId);
        if (Objects.nonNull(keyAlias)) {
            return keyStore.getSymmetricKey(keyAlias);
        }
        throw new CertifyException(NO_UNIQUE_ALIAS);
    }

    private String getKeyAlias(String keyAppId, String keyRefId) {
        Map<String, List<KeyAlias>> keyAliasMap = dbHelper.getKeyAliases(keyAppId, keyRefId, LocalDateTime.now(ZoneOffset.UTC));
        List<KeyAlias> currentKeyAliases = keyAliasMap.get(KeymanagerConstant.CURRENTKEYALIAS);
        if (!currentKeyAliases.isEmpty() && currentKeyAliases.size() == 1) {
            return currentKeyAliases.getFirst().getAlias();
        }
        log.error("CurrentKeyAlias is not unique. KeyAlias count: {}", currentKeyAliases.size());
        throw new CertifyException(NO_UNIQUE_ALIAS);
    }
}
