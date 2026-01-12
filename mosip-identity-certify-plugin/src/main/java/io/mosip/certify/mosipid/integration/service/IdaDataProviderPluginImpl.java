package io.mosip.certify.mosipid.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.mosipid.integration.dto.*;
import io.mosip.certify.mosipid.integration.helper.VCITransactionHelper;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
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

import static io.mosip.esignet.core.constants.Constants.VERIFIED_CLAIMS;

@Component
@Slf4j
@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "IdaDataProviderPluginImpl")
public class IdaDataProviderPluginImpl implements DataProviderPlugin {
    // TODO: Clean up code
    // TODO: Write unit tests

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

    @Value("${mosip.certify.ida.vci-exchange-version}")
    private String vciExchangeVersion;

    @Value("${mosip.certify.cache.secure.individual-id}")
    private boolean secureIndividualId;

    @Value("${mosip.certify.cache.store.individual-id}")
    private boolean storeIndividualId;

    @Value("${mosip.certify.cache.security.algorithm-name}")
    private String aesECBTransformation;

    @Value("${mosip.certify.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("${mosip.certify.authenticator.ida.secret-key}")
    private String secretKey;

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
    VCITransactionHelper vciTransactionHelper;

    private Base64.Decoder urlSafeDecoder = Base64.getUrlDecoder();

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            KycExchangeResult kycExchangeResult = doKycExchange(identityDetails);
            if(kycExchangeResult != null) {
                log.info("Kyc Exchange Success.");
                String encryptedKyc = kycExchangeResult.getEncryptedKyc();
                Map<String, Object> claims = decodeClaimsFromJwt(encryptedKyc);

                return new JSONObject(claims);
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

        log.info("KYC exchange DTO built: {}", kycExchangeDto);

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
        OIDCTransaction transaction = vciTransactionHelper
                .getOAuthTransaction(identityDetails.get(ACCESS_TOKEN_HASH).toString());
        KycExchangeDto kycExchangeDto = buildKycExchangeDto(transaction);
        String relyingPartyId = transaction.getRelyingPartyId();
        String clientId = transaction.getClientId();
        return kycExchange(relyingPartyId, clientId, kycExchangeDto, false);
    }

    private KycExchangeResult kycExchange(String relyingPartyId, String clientId, KycExchangeDto kycExchangeDto,boolean isV2)
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
            idaKycExchangeRequest.setRespType(kycExchangeDto.getUserInfoResponseType()); //may be either JWT or JWE
            idaKycExchangeRequest.setIndividualId(kycExchangeDto.getIndividualId());

            if(isV2){
                setClaims((VerifiedKycExchangeDto) kycExchangeDto, idaKycExchangeRequest);
            }

            log.info("Sending the kyc exchange request : {}", idaKycExchangeRequest);

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

    /**
     * Set the verfied and unVerified consented claims to {@link IdaKycExchangeRequest} object
     * @param kycExchangeDto {@link KycExchangeDto}
     * @param idaKycExchangeRequest {@link IdaKycExchangeRequest}
     */
    private void setClaims(VerifiedKycExchangeDto kycExchangeDto, IdaKycExchangeRequest idaKycExchangeRequest) {
        if(kycExchangeDto != null){
            Map<String, JsonNode> acceptedClaimDetails = kycExchangeDto.getAcceptedClaimDetails();
            if(acceptedClaimDetails!=null && acceptedClaimDetails.get(VERIFIED_CLAIMS)!=null){
                List<Map<String, Object>> verifiedClaimsList = objectMapper.convertValue(kycExchangeDto.getAcceptedClaimDetails()
                        .get(VERIFIED_CLAIMS), new TypeReference<>() {});
                idaKycExchangeRequest.setVerifiedConsentedClaims(verifiedClaimsList);
            }

            idaKycExchangeRequest.setUnVerifiedConsentedClaims(getUnVerifiedConsentedClaims(acceptedClaimDetails));
        }
    }

    /**
     * Method to return un verified consented claims
     * @param acceptedClaimDetails Accepted claims Map
     * @return un verified consented claims
     */
    @NotNull // This is added to not return null either return un verified claims map or empty map
    private Map<String, Object> getUnVerifiedConsentedClaims(Map<String, JsonNode> acceptedClaimDetails) {
        Map<String, JsonNode> unVerifiedConsentedClaims = new HashMap<>();
        if(!CollectionUtils.isEmpty(acceptedClaimDetails)) {
            for(Map.Entry<String, JsonNode> entry : acceptedClaimDetails.entrySet()) {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if(!key.equals(VERIFIED_CLAIMS)){
                    unVerifiedConsentedClaims.put(key,value);
                }
            }
        }
        return objectMapper.convertValue(unVerifiedConsentedClaims, new TypeReference<>() {});
    }

    private Map<String, Object> decodeClaimsFromJwt(String jwtToken) throws JsonProcessingException {
        String[] parts = jwtToken.split("\\.");
        String payload = new String(urlSafeDecoder.decode(parts[1]));
        Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

        return claims;
    }
}
