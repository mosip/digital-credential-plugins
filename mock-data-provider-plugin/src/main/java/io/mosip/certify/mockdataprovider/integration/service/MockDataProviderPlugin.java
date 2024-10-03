package io.mosip.certify.mockdataprovider.integration.service;


import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class MockDataProviderPlugin implements DataProviderPlugin {
    private static final String AES_CIPHER_FAILED = "aes_cipher_failed";
    private static final String NO_UNIQUE_ALIAS = "no_unique_alias";
    private static final String USERINFO_CACHE = "userinfo";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private KeyStore keyStore;

    @Autowired
    private KeymanagerDBHelper dbHelper;

    @Value("${mosip.certify.mock.authenticator.get-identity-url}")
    private String getIdentityUrl;

    @Value("${mosip.certify.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("${mosip.certify.cache.security.algorithm-name}")
    private String aesECBTransformation;

    @Value("${mosip.certify.cache.secure.individual-id}")
    private boolean secureIndividualId;

    @Value("${mosip.certify.cache.store.individual-id}")
    private boolean storeIndividualId;

    @Value("#{${mosip.certify.mock.vciplugin.vc-credential-contexts:{'https://www.w3.org/2018/credentials/v1','https://schema.org/'}}}")
    private List<String> vcCredentialContexts;

    private static final String ACCESS_TOKEN_HASH = "accessTokenHash";

    public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final String CERTIFY_SERVICE_APP_ID = "CERTIFY_SERVICE";

    @Override
    public Map<String, Object> fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {

        Map<String, Object> dataProviderMap = null;
        try {
            dataProviderMap = buildDataJson(identityDetails.get(ACCESS_TOKEN_HASH).toString());
            return dataProviderMap;
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
        }

        throw new DataProviderExchangeException();
    }

    private Map<String, Object> buildDataJson(String accessHashToken) throws IOException, GeneralSecurityException, URISyntaxException {
        OIDCTransaction transaction = getUserInfoTransaction(accessHashToken);
        Map<String, Object> formattedMap = null;
        try {
            formattedMap = getIndividualData(transaction);
        } catch (Exception e) {
            log.error("Unable to get KYC exchange data from MOCK", e);
        }
        return formattedMap;
    }

    private Map<String, Object> getIndividualData(OIDCTransaction transaction) {
        String individualId = getIndividualId(transaction);
        if (individualId != null) {
            Map<String, Object> res = new RestTemplate().getForObject(
                    getIdentityUrl + "/" + individualId,
                    HashMap.class);
            res = (Map<String, Object>) res.get("response");
            Map<String, Object> ret = new HashMap<>();
            ret.put("vcVer", "VC-V1");
            ret.put("id", getIdentityUrl + "/" + individualId);
            ret.put("UIN", individualId);
            ret.put("fullName", res.get("fullName"));
            ret.put("gender", res.get("gender"));
            ret.put("dateOfBirth", res.get("dateOfBirth"));
            ret.put("email", res.get("email"));
            ret.put("phone", res.get("phone"));
            ret.put("addressLine1", res.get("streetAddress"));
            ret.put("province", res.get("locality"));
            ret.put("region", res.get("region"));
            ret.put("postalCode", res.get("postalCode"));
            ret.put("face", res.get("encodedPhoto"));
            ret.put("issuer", getIdentityUrl + "/" + individualId);
            return ret;
        } else {
            return new HashMap<>();
        }
    }

    protected String getIndividualId(OIDCTransaction transaction) {
        if (!storeIndividualId)
            return null;
        return secureIndividualId ? decryptIndividualId(transaction.getIndividualId()) : transaction.getIndividualId();
    }

    private String decryptIndividualId(String encryptedIndividualId) {
        try {
            Cipher cipher = Cipher.getInstance(aesECBTransformation);
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encryptedIndividualId);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKeyFromHSM());
            return new String(cipher.doFinal(decodedBytes, 0, decodedBytes.length));
        } catch (Exception e) {
            log.error("Error Cipher Operations of provided secret data.", e);
            throw new CertifyException(AES_CIPHER_FAILED);
        }
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
        if (currentKeyAliases != null && currentKeyAliases.size() == 1) {
            return currentKeyAliases.get(0).getAlias();
        }
        log.error("CurrentKeyAlias is not unique. KeyAlias count: {}", currentKeyAliases.size());
        throw new CertifyException(NO_UNIQUE_ALIAS);
    }

    private static String getUTCDateTime() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
    }

    public OIDCTransaction getUserInfoTransaction(String accessTokenHash) {
        return cacheManager.getCache(USERINFO_CACHE).get(accessTokenHash, OIDCTransaction.class);
    }
}
