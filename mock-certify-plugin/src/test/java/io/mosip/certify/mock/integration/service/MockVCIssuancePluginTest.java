package io.mosip.certify.mock.integration.service;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.core.dto.ParsedAccessToken;
import io.mosip.esignet.core.dto.OIDCTransaction;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class MockVCIssuancePluginTest {


    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache=new NoOpCache("test");

    @InjectMocks
    MockVCIssuancePlugin mockVCIssuancePlugin = new MockVCIssuancePlugin();

    @Mock
    private SignatureService signatureService;

    private VCRequestDto vcRequestDto = new VCRequestDto();


    @Before
    public void setUp() {
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"getIdentityUrl","http://example.com");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"verificationMethod","http://example.com/verify");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(mockVCIssuancePlugin,"secureIndividualId",false);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
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
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidDetails_thenPass() throws VCIExchangeException {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        when(signatureService.jwtSign(any())).thenReturn(jwtSignatureResponseDto);
        VCResult vcResult = mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(vcResult.getCredential());
        Assert.assertEquals(vcResult.getFormat(),"ldp_vc");
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
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        when(signatureService.jwtSign(any())).thenReturn(jwtSignatureResponseDto);
        VCResult vcResult = mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(vcResult.getCredential());
        JsonLDObject credential = (JsonLDObject) vcResult.getCredential();
        Assert.assertNotNull(credential.getTypes());
        List<String> expectedType = Arrays.asList("VerifiableCredential", "MockVerifiableCredential");
        Assert.assertEquals(expectedType, credential.getTypes());
    }

    @Test(expected = VCIExchangeException.class)
    public void getVerifiableCredentialWithLinkedDataProof_whenBuildJsonLDFails_shouldThrowException() throws Exception {
        // Setup transaction to return null individual data
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setIndividualId(null);
        when(cache.get(anyString(), eq(OIDCTransaction.class))).thenReturn(transaction);

        // This should cause the buildJsonLDWithLDProof to fail
        mockVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(
                vcRequestDto,
                "holderId",
                Map.of("accessTokenHash", "ACCESS_TOKEN_HASH")
        );
    }

    @Test(expected = VCIExchangeException.class)
    public void getVerifiableCredential_shouldThrowNotImplemented() throws VCIExchangeException {
        mockVCIssuancePlugin.getVerifiableCredential(vcRequestDto, "holderId", Map.of());
    }
}
