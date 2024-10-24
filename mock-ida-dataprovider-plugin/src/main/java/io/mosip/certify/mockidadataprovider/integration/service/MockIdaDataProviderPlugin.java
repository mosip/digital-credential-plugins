package io.mosip.certify.mockidadataprovider.integration.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.certify.mockidadataprovider.integration.repository.MockDataRepository;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockIdaDataProviderPlugin")
@Component
@Slf4j
public class MockIdaDataProviderPlugin implements DataProviderPlugin {
    private static final String AES_CIPHER_FAILED = "aes_cipher_failed";
    private static final String NO_UNIQUE_ALIAS = "no_unique_alias";

    private static final String ACCESS_TOKEN_HASH = "accessTokenHash";

    public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static final String CERTIFY_SERVICE_APP_ID = "CERTIFY_SERVICE";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private KeyStore keyStore;

    @Autowired
    private KeymanagerDBHelper dbHelper;

    @Autowired
    private MockTransactionHelper mockTransactionHelper;

    @Autowired
    private MockDataRepository mockDataRepository;

    @Value("${mosip.certify.mock.authenticator.get-identity-url}")
    private String getIdentityUrl;

    @Value("${mosip.certify.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("${mosip.certify.cache.security.algorithm-name}")
    private String aesECBTransformation;

    @Value("${mosip.certify.cache.secure.individual-id}")
    private boolean isIndividualIDEncrypted;

    @Value("${mosip.certify.cache.store.individual-id}")
    private boolean storeIndividualId;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            OIDCTransaction transaction = mockTransactionHelper.getUserInfoTransaction(identityDetails.get(ACCESS_TOKEN_HASH).toString());
            String individualId = getIndividualId(transaction);

            if (individualId != null) {
                Object[] mockData = mockDataRepository.getIdentityDataFromIndividualId(individualId);
                Map<String, Object> mockDataMap = new HashMap<>();
                try {
                    mockDataMap = objectMapper.readValue(mockData[1].toString(), HashMap.class);
                    log.info("mock data map " + mockDataMap);
                } catch (Exception e) {
                    log.error("mock data not present");
                }
                JSONObject jsonRes = new JSONObject(mockDataMap);
                return jsonRes;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_IDENTITY_DATA");
        }

        throw new DataProviderExchangeException("INVALID_ACCESS_TOKEN");
    }

    protected String getIndividualId(OIDCTransaction transaction) {
        if (!storeIndividualId)
            return null;
        return isIndividualIDEncrypted ? decryptIndividualId(transaction.getIndividualId()) : transaction.getIndividualId();
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
}