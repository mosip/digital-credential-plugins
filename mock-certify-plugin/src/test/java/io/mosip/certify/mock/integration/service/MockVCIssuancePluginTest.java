package io.mosip.certify.mock.integration.service;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.core.dto.ParsedAccessToken;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCache;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.*;

import static io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant.CURRENTKEYALIAS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class MockVCIssuancePluginTest {


    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache=new NoOpCache("test");

    @Mock
    KeymanagerDBHelper keymanagerDBHelper;

    @Mock
    KeyStore keyStore;

    @InjectMocks
    MockVCIssuancePlugin mockVCIssuancePlugin = new MockVCIssuancePlugin();

    @Mock
    private SignatureService signatureService;

    private VCRequestDto vcRequestDto = new VCRequestDto();

    private OIDCTransaction oidcTransaction;

    @Mock
    RestTemplate restTemplate;

    private static final String MOCK_ACCESS_TOKEN_HASH = "ACCESS_TOKEN_HASH";
    private static final String MOCK_HOLDER_ID = "holderId";
    private static final String MOCK_INDIVIDUAL_ID = "individualId";
    private static final String MOCK_ENCRYPTED_INDIVIDUAL_ID = "encryptedIndividualId";
    private static final String MOCK_GET_IDENTITY_URL = "http://example.com";
    private static final String MOCK_SECRET_KEY_REF_ID = "cacheSecretKeyRefId";
    private static final String MOCK_AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String MOCK_VERIFICATION_METHOD = "http://example.com/verify";
    private static final String MOCK_JWT_SIGNED_DATA = "test-jwt";


    @Before
    public void setUp() {
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"getIdentityUrl","http://example.com");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"verificationMethod","http://example.com/verify");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"secureIndividualId",false);

        oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");
        oidcTransaction.setClaimsLocales(new String[]{"en-US", "en", "en-CA", "fr-FR", "fr-CA"});

        vcRequestDto.setFormat("ldp_vc");
        vcRequestDto.setContext(Arrays.asList("context1","context2"));
        vcRequestDto.setType(Arrays.asList("VerifiableCredential", "MockVerifiableCredential"));
        vcRequestDto.setCredentialSubject(Map.of("subject1","subject1","subject2","subject2"));

        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(cache.get(MOCK_ACCESS_TOKEN_HASH, OIDCTransaction.class)).thenReturn(oidcTransaction);
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("response", Map.of(
                "fullName", "Mock User",
                "gender", "Male",
                "dateOfBirth", "1990-01-01",
                "email", "mockuser@example.com",
                "phone", "1234567890",
                "streetAddress", "123 Mock Street",
                "locality", "Mock City",
                "region", "Mock State",
                "postalCode", "123456",
                "encodedPhoto", "mockPhotoData"
        ));
        when(restTemplate.getForObject(anyString(), eq(HashMap.class))).thenReturn((HashMap) mockResponse);
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidDetails_thenPass() throws VCIExchangeException {
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        when(signatureService.jwtSign(any())).thenReturn(jwtSignatureResponseDto);
        VCResult vcResult = mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(vcResult.getCredential());
        Assert.assertEquals(vcResult.getFormat(),"ldp_vc");
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidDetails_Success() throws Exception {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData(MOCK_JWT_SIGNED_DATA);
        when(signatureService.jwtSign(any(JWTSignatureRequestDto.class))).thenReturn(jwtSignatureResponseDto);

        // Act
        VCResult<JsonLDObject> result = mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto, MOCK_HOLDER_ID, Map.of("accessTokenHash", MOCK_ACCESS_TOKEN_HASH));

        // Assert
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getCredential());
        Assert.assertEquals("ldp_vc", result.getFormat());
        verify(signatureService).jwtSign(any(JWTSignatureRequestDto.class));
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidDetailsAndStoreIndividualId_thenPass() throws Exception {
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"secureIndividualId",true);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        String individualId = encryptIndividualId("individualId",key);
        oidcTransaction.setIndividualId(individualId);

        Map<String, List<KeyAlias>> keyaliasesMap = new HashMap<>();
        KeyAlias keyAlias = new KeyAlias();
        keyAlias.setAlias("test");
        keyaliasesMap.put(CURRENTKEYALIAS, Arrays.asList(keyAlias));
        Mockito.when(keymanagerDBHelper.getKeyAliases(Mockito.anyString(), Mockito.anyString(), Mockito.any(LocalDateTime.class))).thenReturn(keyaliasesMap);
        Mockito.when(keyStore.getSymmetricKey(Mockito.anyString())).thenReturn(key, key);

        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(cache.get(MOCK_ACCESS_TOKEN_HASH, OIDCTransaction.class)).thenReturn(oidcTransaction);

        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData(MOCK_JWT_SIGNED_DATA);
        when(signatureService.jwtSign(any(JWTSignatureRequestDto.class))).thenReturn(jwtSignatureResponseDto);

        // Act
        VCResult<JsonLDObject> result = mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto, MOCK_HOLDER_ID, Map.of("accessTokenHash", MOCK_ACCESS_TOKEN_HASH));

        // Assert
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getCredential());
        Assert.assertEquals("ldp_vc", result.getFormat());
        verify(signatureService).jwtSign(any(JWTSignatureRequestDto.class));
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withoutSignatureService_thenFail() {
        try{
            VCResult result=  mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
            Assert.fail();
        }catch (Exception e) {
            Assert.assertEquals("vci_exchange_failed",e.getMessage());
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidCredentialType() throws VCIExchangeException {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData(MOCK_JWT_SIGNED_DATA);
        when(signatureService.jwtSign(any(JWTSignatureRequestDto.class))).thenReturn(jwtSignatureResponseDto);
        VCResult vcResult = mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(vcResult.getCredential());
        JsonLDObject credential = (JsonLDObject) vcResult.getCredential();
        Assert.assertNotNull(credential.getTypes());
        List<String> expectedType = Arrays.asList("VerifiableCredential", "MockVerifiableCredential");
        Assert.assertEquals(expectedType, credential.getTypes());
    }

    @Test(expected = VCIExchangeException.class)
    public void getVerifiableCredential_shouldThrowNotImplemented() throws VCIExchangeException {
        mockVCIssuancePlugin.getVerifiableCredential(vcRequestDto, "holderId", Map.of());
    }

    private String encryptIndividualId(String individualId, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            byte[] secretDataBytes = individualId.getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE,key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(secretDataBytes, 0, secretDataBytes.length));
        } catch(Exception e) {
            throw new CertifyException("aes_cipher_failed");
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withInValidIndividualId_thenFail() throws Exception {
        oidcTransaction.setIndividualId(null);
        when(cache.get(MOCK_ACCESS_TOKEN_HASH, OIDCTransaction.class)).thenReturn(oidcTransaction);

        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData(MOCK_JWT_SIGNED_DATA);
        when(signatureService.jwtSign(any(JWTSignatureRequestDto.class))).thenReturn(jwtSignatureResponseDto);

        VCResult result=  mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getCredential());
        Assert.assertEquals("ldp_vc", result.getFormat());
        verify(signatureService).jwtSign(any(JWTSignatureRequestDto.class));
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withInValidDetails_thenFail() throws Exception {
        try {
            mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto, "holderId", Map.of("accessTokenHash", "ACCESS_TOKEN_HASH", "client_id", "CLIENT_ID"));
            Assert.fail();
        } catch (VCIExchangeException e) {
            Assert.assertEquals("vci_exchange_failed", e.getErrorCode());
        }
    }
}