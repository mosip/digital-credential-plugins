package io.mosip.certify.mosipid.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.mosipid.integration.dto.*;
import io.mosip.certify.mosipid.integration.helper.ImageCompressorUtil;
import io.mosip.certify.mosipid.integration.helper.TransactionHelper;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Cipher;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
@Slf4j
@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "IdaDataProviderPluginImpl")
public class IdaDataProviderPluginImpl implements DataProviderPlugin {
    // TODO: Clean up code
    // TODO: Write unit tests

    static {
        /**
         * load OpenCV library nu.pattern.OpenCV.loadShared();
         * System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
         */
        /**
         * In Java >= 12 it is no longer possible to use addLibraryPath, which modifies
         * the ClassLoader's static usr_paths field. There does not seem to be any way
         * around this so we fall back to loadLocally() and return.
         */
        nu.pattern.OpenCV.loadLocally();
        Loader.load(opencv_java.class);
        System.setProperty("OPENCV_IO_ENABLE_JASPER", "1");
    }

    private static final String ACCESS_TOKEN_HASH = "accessTokenHash";
    public static final String SIGNATURE_HEADER_NAME = "signature";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String OIDC_SERVICE_APP_ID = "CERTIFY_SERVICE";
    public static final String AES_CIPHER_FAILED = "aes_cipher_failed";
    public static final String NO_UNIQUE_ALIAS = "no_unique_alias";

    @Value("${mosip.certify.authenticator.ida-version:1.0}")
    private String idaVersion;

    @Value("${mosip.certify.authenticator.ida.kyc-exchange-url}")
    private String kycExchangeUrl;

    @Value("${mosip.certify.ida.kyc-exchange-id:mosip.identity.kycexchange}")
    private String kycExchangeId;

    @Value("${mosip.certify.cache.secure.individual-id}")
    private boolean secureIndividualId;

    @Value("${mosip.certify.cache.store.individual-id}")
    private boolean storeIndividualId;

    @Value("${mosip.certify.cache.security.algorithm-name}")
    private String aesECBTransformation;

    @Value("${mosip.certify.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("#{'${mosip.certify.ida.kyc-exchange.accepted-claims}'.split(',')}")
    private List<String> kycExchangeAcceptedClaims;

    @Value("${mosip.certify.ida.kyc-exchange.accepted-locales:en}")
    private String[] kycAcceptedLocales;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    HelperService helperService;

    @Autowired
    private KeyStore keyStore;

    @Autowired
    private KeymanagerDBHelper dbHelper;

    @Autowired
    TransactionHelper transactionHelper;

    @Autowired
    private ImageCompressorUtil imageCompressorUtil;

    private Base64.Decoder urlSafeDecoder = Base64.getUrlDecoder();

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            KycExchangeResult kycExchangeResult = doKycExchange(identityDetails);
            if(kycExchangeResult != null) {
                log.info("Kyc Exchange Success.");
                String encryptedKyc = kycExchangeResult.getEncryptedKyc();
                log.debug("Encrypted KYC: {}", encryptedKyc);
                Map<String, Object> claims = decodeClaimsFromJwt(encryptedKyc);
                log.debug("JWT Claims: {}", claims);

                JSONObject jsonRes = new JSONObject(claims);

                if(jsonRes.has("picture")) {
                    String imageData = jsonRes.getString("picture");
                    String compressedImageData = imageCompressorUtil.extractAndCompressImage(imageData);
                    jsonRes.put("compressedPicture", compressedImageData);
                }
                return jsonRes;
            }
        }
        catch (JSONException | JsonProcessingException e) {
            log.error("Error occurred during json processing: " + e.getMessage());
            throw new DataProviderExchangeException("JSON_PARSING_FAILED", e.getMessage());
        }
        catch (Exception e) {
            log.error("ERROR_FETCHING_KYC_DATA. " +  e.getMessage());
            throw new DataProviderExchangeException("ERROR_FETCHING_KYC_DATA", e.getMessage());
        }
        throw new DataProviderExchangeException("ERROR_FETCHING_KYC_DATA", "No data found for kyc exchange");
    }

    private KycExchangeDto buildKycExchangeDto(OIDCTransaction transaction) throws Exception {
        KycExchangeDto kycExchangeDto = new KycExchangeDto();

        String individualId = getIndividualId(transaction.getIndividualId());
        kycExchangeDto.setIndividualId(individualId);
        kycExchangeDto.setTransactionId(transaction.getAuthTransactionId());
        kycExchangeDto.setKycToken(transaction.getKycToken());
        kycExchangeDto.setAcceptedClaims(kycExchangeAcceptedClaims);
        kycExchangeDto.setClaimsLocales(kycAcceptedLocales);
        kycExchangeDto.setUserInfoResponseType(null);

        log.info("Built the KYC exchange DTO");

        return kycExchangeDto;
    }

    protected String getIndividualId(String encryptedIndividualId) throws Exception {
        if (!storeIndividualId)
            return null;
        return secureIndividualId ? decryptIndividualId(encryptedIndividualId) : encryptedIndividualId;
    }

    private String decryptIndividualId(String encryptedIndividualId) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(aesECBTransformation);
            byte[] decodedBytes = b64Decode(encryptedIndividualId);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKeyFromHSM());
            return new String(cipher.doFinal(decodedBytes, 0, decodedBytes.length));
        } catch (Exception e) {
            log.error("Error Cipher Operations of provided secret data.", e);
            throw new Exception(AES_CIPHER_FAILED);
        }
    }

    private Key getSecretKeyFromHSM() throws Exception {
        String keyAlias = getKeyAlias(OIDC_SERVICE_APP_ID, cacheSecretKeyRefId);
        if (Objects.nonNull(keyAlias)) {
            return keyStore.getSymmetricKey(keyAlias);
        }
        throw new Exception(NO_UNIQUE_ALIAS);
    }

    private String getKeyAlias(String keyAppId, String keyRefId) throws Exception {
        Map<String, List<KeyAlias>> keyAliasMap = dbHelper.getKeyAliases(keyAppId, keyRefId,
                LocalDateTime.now(ZoneOffset.UTC));
        List<KeyAlias> currentKeyAliases = keyAliasMap.get(KeymanagerConstant.CURRENTKEYALIAS);
        if (!currentKeyAliases.isEmpty() && currentKeyAliases.size() == 1) {
            return currentKeyAliases.get(0).getAlias();
        }
        log.error("CurrentKeyAlias is not unique. KeyAlias count: {}", currentKeyAliases.size());
        throw new Exception(NO_UNIQUE_ALIAS);
    }

    private byte[] b64Decode(String value) {
        return urlSafeDecoder.decode(value);
    };

    public KycExchangeResult doKycExchange(Map<String, Object> identityDetails)
            throws Exception {
        OIDCTransaction transaction = transactionHelper
                .getOAuthTransaction(identityDetails.get(ACCESS_TOKEN_HASH).toString());
        KycExchangeDto kycExchangeDto = buildKycExchangeDto(transaction);
        String relyingPartyId = transaction.getRelyingPartyId();
        String clientId = transaction.getClientId();
        return kycExchange(relyingPartyId, clientId, kycExchangeDto);
    }

    private KycExchangeResult kycExchange(String relyingPartyId, String clientId, KycExchangeDto kycExchangeDto)
            throws KycExchangeException {
        log.info("Started to build kyc-exchange request with transactionId : {} && clientId : {}",
                kycExchangeDto.getTransactionId(), clientId);
        try {
            IdaKycExchangeRequest idaKycExchangeRequest = new IdaKycExchangeRequest();
            idaKycExchangeRequest.setId(kycExchangeId);
            idaKycExchangeRequest.setVersion(idaVersion);
            idaKycExchangeRequest.setRequestTime(HelperService.getUTCDateTime());
            idaKycExchangeRequest.setTransactionID(kycExchangeDto.getTransactionId());
            idaKycExchangeRequest.setKycToken(kycExchangeDto.getKycToken());
            idaKycExchangeRequest.setConsentObtained(kycExchangeDto.getAcceptedClaims());
            idaKycExchangeRequest.setLocales(helperService.convertLangCodesToISO3LanguageCodes(kycExchangeDto.getClaimsLocales()));
            idaKycExchangeRequest.setRespType(kycExchangeDto.getUserInfoResponseType()); // Setting the Response Type to null will give the final result as a JWT
            idaKycExchangeRequest.setIndividualId(kycExchangeDto.getIndividualId());

            log.info("Built the kyc exchange request");

            //set signature header, body and invoke kyc exchange endpoint
            String requestBody = objectMapper.writeValueAsString(idaKycExchangeRequest);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(kycExchangeUrl).pathSegment(relyingPartyId,
                            clientId).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .header(SIGNATURE_HEADER_NAME, helperService.getRequestSignature(requestBody))
                    .header(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_NAME)
                    .body(requestBody);
            ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>() {});

            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                IdaResponseWrapper<IdaKycExchangeResponse> responseWrapper = responseEntity.getBody();
                if(responseWrapper.getResponse() != null && responseWrapper.getResponse().getEncryptedKyc() != null) {
                    return new KycExchangeResult(responseWrapper.getResponse().getEncryptedKyc());
                }
                log.error("Errors in response received from IDA Kyc Exchange: {}", responseWrapper.getErrors());
                throw new KycExchangeException(CollectionUtils.isEmpty(responseWrapper.getErrors()) ?
                        io.mosip.esignet.api.util.ErrorConstants.DATA_EXCHANGE_FAILED : responseWrapper.getErrors().get(0).getErrorCode());
            }

            log.error("Error response received from IDA (Kyc-exchange) with status : {}", responseEntity.getStatusCode());
        } catch (KycExchangeException e) { throw e; } catch (Exception e) {
            log.error("IDA Kyc-exchange failed with clientId : {}", clientId, e);
        }
        throw new KycExchangeException();
    }

    private Map<String, Object> decodeClaimsFromJwt(String jwtToken) throws JsonProcessingException, DataProviderExchangeException {
        String[] parts = jwtToken.split("\\.");
        if(parts.length < 3) {
            throw new DataProviderExchangeException("Invalid KYC Exchange response.");
        }
        String payload = new String(urlSafeDecoder.decode(parts[1]));
        Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

        return claims;
    }
}
