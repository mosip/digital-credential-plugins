/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.certify.mosipid.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.api.util.ErrorConstants;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.certify.mosipid.integration.dto.*;
import io.mosip.certify.mosipid.integration.helper.TransactionHelper;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.*;

import static io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant.CURRENTKEYALIAS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class IdaVCIssuancePluginImplTest {

    @Mock
    TransactionHelper transactionHelper;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    RestTemplate restTemplate;

    @Mock
    HelperService helperService;

    @Mock
    KeymanagerDBHelper keymanagerDBHelper;

    @Mock
    KeyStore keyStore;

    @InjectMocks
    IdaVCIssuancePluginImpl idaVCIssuancePlugin=new IdaVCIssuancePluginImpl();

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidDetails_thenPass() throws Exception {

        ReflectionTestUtils.setField(idaVCIssuancePlugin,"vciExchangeUrl","http://example.com");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ldp_vc");
        vcRequestDto.setContext(Arrays.asList("context1","context2"));
        vcRequestDto.setType(Arrays.asList("VerifiableCredential"));
        vcRequestDto.setCredentialSubject(Map.of("subject1","subject1","subject2","subject2"));

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");
        oidcTransaction.setClaimsLocales(new String[]{"en-US", "en", "en-CA", "fr-FR", "fr-CA"});

        IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>> mockResponseWrapper = new IdaResponseWrapper<>();
        IdaVcExchangeResponse<JsonLDObject> mockResponse = new IdaVcExchangeResponse<>();
        JsonLDObject jsonLDObject = new JsonLDObject();
        jsonLDObject.setJsonObjectKeyValue("key", "value");
        mockResponse.setVerifiableCredentials(jsonLDObject);
        mockResponseWrapper.setResponse(mockResponse);
        mockResponseWrapper.setId("id");
        mockResponseWrapper.setVersion("version");
        mockResponseWrapper.setTransactionID("transactionID");

        ResponseEntity<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>> mockResponseEntity = ResponseEntity.ok(mockResponseWrapper);
        ParameterizedTypeReference<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>> responseType =
                new ParameterizedTypeReference<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>>() {
                };

        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenReturn(oidcTransaction);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any(IdaVcExchangeRequest.class))).thenReturn("jsonString");
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(responseType)
        )).thenReturn(mockResponseEntity);

        VCResult result=idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(result.getCredential());
        Assert.assertEquals(jsonLDObject,result.getCredential());
        Assert.assertEquals(result.getFormat(),"ldp_vc");
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withValidDetailsAndStoreIndividualId_thenPass() throws Exception {

        ReflectionTestUtils.setField(idaVCIssuancePlugin,"vciExchangeUrl","http://example.com");
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"secureIndividualId",true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ldp_vc");
        vcRequestDto.setContext(Arrays.asList("context1","context2"));
        vcRequestDto.setType(Arrays.asList("VerifiableCredential"));
        vcRequestDto.setCredentialSubject(Map.of("subject1","subject1","subject2","subject2"));

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        String individualId = encryptIndividualId("individual-id",key);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId(individualId);
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");

        Map<String, List<KeyAlias>> keyaliasesMap = new HashMap<>();
        KeyAlias keyAlias = new KeyAlias();
        keyAlias.setAlias("test");
        keyaliasesMap.put(CURRENTKEYALIAS, Arrays.asList(keyAlias));
        Mockito.when(keymanagerDBHelper.getKeyAliases(Mockito.anyString(), Mockito.anyString(), Mockito.any(LocalDateTime.class))).thenReturn(keyaliasesMap);
        Mockito.when(keyStore.getSymmetricKey(Mockito.anyString())).thenReturn(key, key);

        IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>> mockResponseWrapper = new IdaResponseWrapper<>();
        IdaVcExchangeResponse<JsonLDObject> mockResponse = new IdaVcExchangeResponse<>();
        JsonLDObject jsonLDObject = new JsonLDObject();
        jsonLDObject.setJsonObjectKeyValue("key", "value");
        mockResponse.setVerifiableCredentials(jsonLDObject);
        mockResponseWrapper.setResponse(mockResponse);
        mockResponseWrapper.setId("id");
        mockResponseWrapper.setVersion("version");
        mockResponseWrapper.setTransactionID("transactionID");

        ResponseEntity<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>> mockResponseEntity = ResponseEntity.ok(mockResponseWrapper);
        ParameterizedTypeReference<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>> responseType =
                new ParameterizedTypeReference<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>>() {
                };

        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenReturn(oidcTransaction);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn("jsonString");
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(responseType)
        )).thenReturn(mockResponseEntity);

        VCResult result=idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(result.getCredential());
        Assert.assertEquals(jsonLDObject,result.getCredential());
        Assert.assertEquals(result.getFormat(),"ldp_vc");
        Mockito.verify(keymanagerDBHelper).getKeyAliases(Mockito.anyString(), Mockito.anyString(), Mockito.any(LocalDateTime.class));
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withInValidIndividualId_thenFail() throws Exception {

        ReflectionTestUtils.setField(idaVCIssuancePlugin,"vciExchangeUrl","http://example.com");
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"secureIndividualId",true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ld_vc");
        vcRequestDto.setContext(Arrays.asList("context1","context2"));
        vcRequestDto.setType(Arrays.asList("VerifiableCredential"));
        vcRequestDto.setCredentialSubject(Map.of("subject1","subject1","subject2","subject2"));

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");

        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenReturn(oidcTransaction);
        try{
            VCResult result=  idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
            Assert.fail();
        }catch (Exception e)
        {
            Assert.assertEquals("vci_exchange_failed",e.getMessage());
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withInValidDetails_thenFail() throws Exception {

        ReflectionTestUtils.setField(idaVCIssuancePlugin,"vciExchangeUrl","http://example.com");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ldp_vc");
        vcRequestDto.setContext(Arrays.asList("context1","context2"));
        vcRequestDto.setType(Arrays.asList("VerifiableCredential"));
        vcRequestDto.setCredentialSubject(Map.of("subject1","subject1","subject2","subject2"));

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");
        oidcTransaction.setClaimsLocales(new String[]{"en-US", "en", "en-CA", "fr-FR", "fr-CA"});
        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenThrow(new VCIExchangeException("IDA-VCI-003"));
        try {
            idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto, "holderId", Map.of("accessTokenHash", "ACCESS_TOKEN_HASH", "client_id", "CLIENT_ID"));
            Assert.fail();
        } catch (VCIExchangeException e) {
            Assert.assertEquals("IDA-VCI-003", e.getErrorCode());
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_withInValidResponse_thenFail() throws Exception {

        ReflectionTestUtils.setField(idaVCIssuancePlugin,"vciExchangeUrl","http://example.com");
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"secureIndividualId",true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(idaVCIssuancePlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ldp_vc");
        vcRequestDto.setContext(Arrays.asList("context1","context2"));
        vcRequestDto.setType(Arrays.asList("VerifiableCredential"));
        vcRequestDto.setCredentialSubject(Map.of("subject1","subject1","subject2","subject2"));

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        String individualId = encryptIndividualId("individual-id",key);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId(individualId);
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");

        Map<String, List<KeyAlias>> keyaliasesMap = new HashMap<>();
        KeyAlias keyAlias = new KeyAlias();
        keyAlias.setAlias("test");
        keyaliasesMap.put(CURRENTKEYALIAS, Arrays.asList(keyAlias));
        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenReturn(oidcTransaction);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn("jsonString");
        Mockito.when(keymanagerDBHelper.getKeyAliases(Mockito.anyString(), Mockito.anyString(), Mockito.any(LocalDateTime.class))).thenReturn(keyaliasesMap);
        Mockito.when(keyStore.getSymmetricKey(Mockito.anyString())).thenReturn(key, key);

        IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>> mockResponseWrapper = new IdaResponseWrapper<>();
        IdaVcExchangeResponse<JsonLDObject> mockResponse = new IdaVcExchangeResponse<>();
        JsonLDObject jsonLDObject = new JsonLDObject();
        jsonLDObject.setJsonObjectKeyValue("key", "value");
        mockResponse.setVerifiableCredentials(jsonLDObject);
        mockResponseWrapper.setResponse(null);
        mockResponseWrapper.setId("id");
        mockResponseWrapper.setVersion("version");
        mockResponseWrapper.setTransactionID("transactionID");

        ResponseEntity<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>> mockResponseEntity = ResponseEntity.ok(mockResponseWrapper);
        ParameterizedTypeReference<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>> responseType =
                new ParameterizedTypeReference<IdaResponseWrapper<IdaVcExchangeResponse<JsonLDObject>>>() {
                };
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(responseType)
        )).thenReturn(mockResponseEntity);

        try{
            VCResult result=  idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
            Assert.fail();
        }catch (Exception e) {
            Assert.assertEquals("vci_exchange_failed",e.getMessage());
        }
    }

    private String encryptIndividualId(String individualId, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            byte[] secretDataBytes = individualId.getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE,key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(secretDataBytes, 0, secretDataBytes.length));
        } catch(Exception e) {
            throw new CertifyException(IdaVCIssuancePluginImpl.AES_CIPHER_FAILED);
        }
    }

    @Test
    public void getVerifiableCredential_shouldThrowNotImplementedException() {
        try {
            idaVCIssuancePlugin.getVerifiableCredential(new VCRequestDto(), "holderId", Map.of());
            Assert.fail();
        } catch (VCIExchangeException e) {
            Assert.assertEquals(ErrorConstants.NOT_IMPLEMENTED, e.getErrorCode());
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_whenRestTemplateThrowsException_thenFail() throws Exception {
        ReflectionTestUtils.setField(idaVCIssuancePlugin, "vciExchangeUrl", "http://example.com");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ldp_vc");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");

        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenReturn(oidcTransaction);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn("jsonString");
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Connection failed"));

        try {
            idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(
                    vcRequestDto, "holderId", Map.of("accessTokenHash", "ACCESS_TOKEN_HASH", "client_id", "CLIENT_ID"));
            Assert.fail();
        } catch (VCIExchangeException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_whenObjectMapperThrowsException_thenFail() throws Exception {
        ReflectionTestUtils.setField(idaVCIssuancePlugin, "vciExchangeUrl", "http://example.com");

        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat("ldp_vc");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");

        Mockito.when(transactionHelper.getOAuthTransaction(Mockito.any())).thenReturn(oidcTransaction);
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonProcessingException("Error") {});

        try {
            idaVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(
                    vcRequestDto, "holderId", Map.of("accessTokenHash", "ACCESS_TOKEN_HASH", "client_id", "CLIENT_ID"));
            Assert.fail();
        } catch (VCIExchangeException e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void getIndividualId_whenStoreIndividualIdFalse_shouldReturnNull() throws Exception {
        ReflectionTestUtils.setField(idaVCIssuancePlugin, "storeIndividualId", false);
        String result = idaVCIssuancePlugin.getIndividualId("test");
        Assert.assertNull(result);
    }

    @Test
    public void getIndividualId_whenSecureIndividualIdFalse_shouldReturnOriginalValue() throws Exception {
        ReflectionTestUtils.setField(idaVCIssuancePlugin, "storeIndividualId", true);
        ReflectionTestUtils.setField(idaVCIssuancePlugin, "secureIndividualId", false);
        String expected = "test";
        String result = idaVCIssuancePlugin.getIndividualId(expected);
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testIdaVcExchangeRequest() {
        IdaVcExchangeRequest req1 = new IdaVcExchangeRequest();
        req1.setVcAuthToken("token");
        req1.setCredSubjectId("subjectId");
        req1.setVcFormat("format");
        req1.setLocales(List.of("en", "fr"));
        req1.setMetadata(Map.of("key", "value"));
        req1.setId("id");
        req1.setVersion("v1");
        req1.setIndividualId("indivId");
        req1.setTransactionID("txnId");
        req1.setRequestTime("now");
        CredentialDefinitionDTO credDef = new CredentialDefinitionDTO();
        req1.setCredentialsDefinition(credDef);

        assertEquals("token", req1.getVcAuthToken());
        assertEquals("subjectId", req1.getCredSubjectId());
        assertEquals("format", req1.getVcFormat());
        assertEquals(List.of("en", "fr"), req1.getLocales());
        assertEquals(Map.of("key", "value"), req1.getMetadata());
        assertEquals("id", req1.getId());
        assertEquals("v1", req1.getVersion());
        assertEquals("indivId", req1.getIndividualId());
        assertEquals("txnId", req1.getTransactionID());
        assertEquals("now", req1.getRequestTime());
        assertEquals(credDef, req1.getCredentialsDefinition());

        IdaVcExchangeRequest req2 = new IdaVcExchangeRequest();
        req2.setVcAuthToken("token");
        req2.setCredSubjectId("subjectId");
        req2.setVcFormat("format");
        req2.setLocales(List.of("en", "fr"));
        req2.setMetadata(Map.of("key", "value"));
        req2.setId("id");
        req2.setVersion("v1");
        req2.setIndividualId("indivId");
        req2.setTransactionID("txnId");
        req2.setRequestTime("now");
        req2.setCredentialsDefinition(credDef);

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertTrue(req1.toString().contains("token"));
    }

    @Test
    public void testIdaVcExchangeResponse() {
        IdaVcExchangeResponse<String> resp1 = new IdaVcExchangeResponse<>();
        resp1.setVerifiableCredentials("vc");
        assertEquals("vc", resp1.getVerifiableCredentials());

        IdaVcExchangeResponse<String> resp2 = new IdaVcExchangeResponse<>();
        resp2.setVerifiableCredentials("vc");

        assertEquals(resp1, resp2);
        assertEquals(resp1.hashCode(), resp2.hashCode());
        assertTrue(resp1.toString().contains("vc"));
    }

    @Test
    public void testIdaError() {
        IdaError err1 = new IdaError();
        err1.setActionMessage("action");
        err1.setErrorCode("code");
        err1.setErrorMessage("msg");

        assertEquals("action", err1.getActionMessage());
        assertEquals("code", err1.getErrorCode());
        assertEquals("msg", err1.getErrorMessage());

        IdaError err2 = new IdaError();
        err2.setActionMessage("action");
        err2.setErrorCode("code");
        err2.setErrorMessage("msg");

        assertEquals(err1, err2);
        assertEquals(err1.hashCode(), err2.hashCode());
        assertTrue(err1.toString().contains("action"));
    }

    @Test
    public void testCredentialDefinitionDTO() {
        CredentialDefinitionDTO dto1 = new CredentialDefinitionDTO();
        dto1.setCredentialSubject(Map.of("key", "value"));
        dto1.setType(List.of("type1", "type2"));
        dto1.setContext(List.of("context1", "context2"));

        assertEquals(Map.of("key", "value"), dto1.getCredentialSubject());
        assertEquals(List.of("type1", "type2"), dto1.getType());
        assertEquals(List.of("context1", "context2"), dto1.getContext());

        CredentialDefinitionDTO dto2 = new CredentialDefinitionDTO();
        dto2.setCredentialSubject(Map.of("key", "value"));
        dto2.setType(List.of("type1", "type2"));
        dto2.setContext(List.of("context1", "context2"));

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertTrue(dto1.toString().contains("key"));
    }

    @Test
    public void testClientIdSecretKeyRequest() {
        // All-args constructor
        ClientIdSecretKeyRequest req1 = new ClientIdSecretKeyRequest("client", "secret", "app");
        assertEquals("client", req1.getClientId());
        assertEquals("secret", req1.getSecretKey());
        assertEquals("app", req1.getAppId());

        // No-args constructor and setters
        ClientIdSecretKeyRequest req2 = new ClientIdSecretKeyRequest();
        req2.setClientId("client");
        req2.setSecretKey("secret");
        req2.setAppId("app");

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertTrue(req1.toString().contains("client"));
    }

    @Test
    public void testIdaResponseWrapper() {
        IdaResponseWrapper<String> wrapper1 = new IdaResponseWrapper<>();
        wrapper1.setId("id1");
        wrapper1.setVersion("v1");
        wrapper1.setTransactionID("txn1");
        wrapper1.setResponseTime("now");
        wrapper1.setResponse("response");
        IdaError error = new IdaError();
        error.setErrorCode("E1");
        wrapper1.setErrors(List.of(error));

        assertEquals("id1", wrapper1.getId());
        assertEquals("v1", wrapper1.getVersion());
        assertEquals("txn1", wrapper1.getTransactionID());
        assertEquals("now", wrapper1.getResponseTime());
        assertEquals("response", wrapper1.getResponse());
        assertEquals(List.of(error), wrapper1.getErrors());

        IdaResponseWrapper<String> wrapper2 = new IdaResponseWrapper<>();
        wrapper2.setId("id1");
        wrapper2.setVersion("v1");
        wrapper2.setTransactionID("txn1");
        wrapper2.setResponseTime("now");
        wrapper2.setResponse("response");
        wrapper2.setErrors(List.of(error));

        assertEquals(wrapper1, wrapper2);
        assertEquals(wrapper1.hashCode(), wrapper2.hashCode());
        assertTrue(wrapper1.toString().contains("id1"));
    }
}
