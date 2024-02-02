package io.mosip.esignet.sunbirdrc.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.exception.VCIExchangeException;
import io.mosip.esignet.api.util.ErrorConstants;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest
public class SunbirdRCVCIssuancePluginTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Environment environment;

    @InjectMocks
    private SunbirdRCVCIssuancePlugin sunbirdRCVCIssuancePlugin;

    private VelocityEngine velocityEngine;

    private Map<String,Map<String,String>> credentialTypeConfigMap;

    private Map<String,Template> credentialTypeTemplatesMap;

    @Autowired
    private ResourceLoader resourceLoader;
    Template template;

    @Before
    public void init(){
        velocityEngine=new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "class");
        velocityEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        template=velocityEngine.getTemplate("InsurenceCredential.json");

        credentialTypeTemplatesMap=new HashMap<>();
        credentialTypeTemplatesMap.put("InsurenceCredential",template);

        credentialTypeConfigMap=new HashMap<>();
        Map<String,String> credentialMap=new HashMap<>();
        credentialMap.put("registry-get-url","url");
        credentialTypeConfigMap.put("InsurenceCredential",credentialMap);

        ReflectionTestUtils.setField(sunbirdRCVCIssuancePlugin,"supportedCredentialTypes",List.of("InsurenceCredential"));
        ReflectionTestUtils.setField(sunbirdRCVCIssuancePlugin,"credentialTypeTemplates",credentialTypeTemplatesMap);
        ReflectionTestUtils.setField(sunbirdRCVCIssuancePlugin,"credentialTypeConfigMap",credentialTypeConfigMap);
    }

    @Test
    public void initialize_ValidDetails_ThenPass() throws IOException, VCIExchangeException {
        File file = new File("src/test/resources/InsurenceCredential.json");
        String credentialPath = "file:/" + file.getAbsolutePath();
        Mockito.when(environment.getProperty(Mockito.anyString())).thenReturn(credentialPath);
        ReflectionTestUtils.invokeMethod(sunbirdRCVCIssuancePlugin, "initialize");
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_ValidDetails_ThenPass() throws VCIExchangeException, JsonProcessingException {
        ReflectionTestUtils.setField(sunbirdRCVCIssuancePlugin,"issueCredentialUrl","https://test.com");
        VCRequestDto vcRequestDto=new VCRequestDto();
        List<String> contextList=List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1");
        vcRequestDto.setContext(contextList);
        vcRequestDto.setFormat("test");
        List<String> types=new ArrayList<>();
        types.add("VerifiableCredential");
        types.add("InsurenceCredential");
        vcRequestDto.setType(types);

        Map<String,Object> identityMap=new HashMap<>();
        identityMap.put("sub","osid");

        Map<String,Object> responseMap=Map.of("policyNumber","654321","dob","654321");

        ResponseEntity<Map<String,Object>>  responseEntity = new ResponseEntity(responseMap, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(new ParameterizedTypeReference<Map<String,Object>>() {}))).thenReturn(responseEntity);

        Map<String,Object> mockChallengMap=new HashMap<>();
        Map<String,Object> credentialSubjectMap=new HashMap<>();
        mockChallengMap.put("@context",List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1"));
        mockChallengMap.put("credentialSubject",credentialSubjectMap);
        Mockito.when(objectMapper.readValue(Mockito.anyString(),Mockito.eq(Map.class))).thenReturn(mockChallengMap);

        VCResult<JsonLDObject> result= sunbirdRCVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",identityMap);
        Assert.assertNotNull(result);
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_InValidRequest_ThenFail() throws VCIExchangeException, JsonProcessingException {
        try{
            sunbirdRCVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(null,"holderId",null);
        }catch (VCIExchangeException e){
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.VCI_EXCHANGE_FAILED);
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_With_EmptyTypes_ThenFail() throws VCIExchangeException, JsonProcessingException {
        try{
            VCRequestDto vcRequestDto=new VCRequestDto();
            List<String> types=new ArrayList<>();
            types.add("InsurenceCredential");
            vcRequestDto.setType(types);
            sunbirdRCVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",null);
        }catch (VCIExchangeException e){
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.VCI_EXCHANGE_FAILED);
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_InValidSupportedCredential_ThenFail()  {
        try{
            VCRequestDto vcRequestDto=new VCRequestDto();
            List<String> types=new ArrayList<>();
            types.add("VerifiableCredential");
            types.add("Credential");
            vcRequestDto.setType(types);
            sunbirdRCVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",null);
        }catch (VCIExchangeException e){
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.VCI_EXCHANGE_FAILED);
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_InValidRegistoryObject_ThenFail() throws VCIExchangeException, JsonProcessingException {
        ReflectionTestUtils.setField(sunbirdRCVCIssuancePlugin,"issueCredentialUrl","https://test.com");
        VCRequestDto vcRequestDto=new VCRequestDto();
        List<String> contextList=List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1");
        vcRequestDto.setContext(contextList);
        vcRequestDto.setFormat("test");
        List<String> types=new ArrayList<>();
        types.add("VerifiableCredential");
        types.add("InsurenceCredential");
        vcRequestDto.setType(types);

        Map<String,Object> identityMap=new HashMap<>();
        identityMap.put("sub","osid");

        Map<String,Object> responseMap=Map.of("policyNumber","654321","dob","654321");
        ResponseEntity<Map<String,Object>>  responseEntity = new ResponseEntity(responseMap, HttpStatus.FORBIDDEN);
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(new ParameterizedTypeReference<Map<String,Object>>() {}))).thenReturn(responseEntity);

        Map<String,Object> mockChallengMap=new HashMap<>();
        Map<String,Object> credentialSubjectMap=new HashMap<>();
        mockChallengMap.put("@context",List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1"));
        mockChallengMap.put("credentialSubject",credentialSubjectMap);
        Mockito.when(objectMapper.readValue(Mockito.anyString(),Mockito.eq(Map.class))).thenReturn(mockChallengMap);

        try{
            sunbirdRCVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",identityMap);
        }catch (VCIExchangeException e){
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.VCI_EXCHANGE_FAILED);
        }
    }

    @Test
    public void getVerifiableCredentialWithLinkedDataProof_InValidCredentialDetails_ThenFail() throws VCIExchangeException, JsonProcessingException {

        VCRequestDto vcRequestDto=new VCRequestDto();
        List<String> contextList=List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1");
        vcRequestDto.setContext(contextList);
        vcRequestDto.setFormat("test");
        List<String> types=new ArrayList<>();
        types.add("VerifiableCredential");
        types.add("InsurenceCredential");
        vcRequestDto.setType(types);

        Map<String,Object> identityMap=new HashMap<>();
        identityMap.put("sub","osid");

        Map<String,Object> responseMap=Map.of("policyNumber","654321","dob","654321");
        ResponseEntity<Map<String,Object>>  responseEntity = new ResponseEntity(responseMap, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.eq(new ParameterizedTypeReference<Map<String,Object>>() {}))).thenReturn(responseEntity);

        Map<String,Object> mockChallengMap=new HashMap<>();
        Map<String,Object> credentialSubjectMap=new HashMap<>();
        mockChallengMap.put("@context",List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1"));
        mockChallengMap.put("credentialSubject",credentialSubjectMap);
        Mockito.when(objectMapper.readValue(Mockito.anyString(),Mockito.eq(Map.class))).thenReturn(mockChallengMap);

        try{
            sunbirdRCVCIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto,"holderId",identityMap);
        }catch (VCIExchangeException e){
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.VCI_EXCHANGE_FAILED);
        }
    }

    @Test
    public void validateAndLoadProperty_With_ValidDetails_ThenPass(){
        Mockito.when(environment.getProperty(Mockito.anyString())).thenReturn("property");
        ReflectionTestUtils.invokeMethod(sunbirdRCVCIssuancePlugin, "validateAndLoadProperty","propertyName","credentialProp",new HashMap<>());

    }

    @Test
    public void validateAndLoadProperty_With_InValidDetails_ThenFail(){
        Mockito.when(environment.getProperty(Mockito.anyString())).thenReturn(null);

        Throwable thrownException = Assert.assertThrows(Throwable.class,
                () -> ReflectionTestUtils.invokeMethod(sunbirdRCVCIssuancePlugin, "validateAndLoadProperty","propertyName","credentialProp",new HashMap<>()));
        Assert.assertTrue(thrownException instanceof UndeclaredThrowableException);
        Throwable actualException = ((UndeclaredThrowableException) thrownException).getUndeclaredThrowable();

        Assert.assertTrue(actualException instanceof VCIExchangeException);
        Assert.assertEquals("Property propertyName is not set Properly.", actualException.getMessage());
    }

    @Test
    public void validateContextUrl_With_InvalidContext_ThenFail() throws JsonProcessingException {

        Map<String,Object> mockChallengMap=new HashMap<>();
        Map<String,Object> credentialSubjectMap=new HashMap<>();
        mockChallengMap.put("@context",List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1"));
        mockChallengMap.put("credentialSubject",credentialSubjectMap);
        Mockito.when(objectMapper.readValue(Mockito.anyString(),Mockito.eq(Map.class))).thenReturn(mockChallengMap);

        Throwable thrownException = Assert.assertThrows(Throwable.class,
                () -> ReflectionTestUtils.invokeMethod(sunbirdRCVCIssuancePlugin, "validateContextUrl", template,  List.of("context1","context2")));

        Assert.assertTrue(thrownException instanceof UndeclaredThrowableException);
        Throwable actualException = ((UndeclaredThrowableException) thrownException).getUndeclaredThrowable();

        Assert.assertTrue(actualException instanceof VCIExchangeException);
        Assert.assertEquals(ErrorConstants.VCI_EXCHANGE_FAILED, actualException.getMessage());
    }

    @Test()
    public void validateContextUrl_With_InvalidTemplate_ThenFail() throws JsonProcessingException {

        Map<String,Object> mockChallengMap=new HashMap<>();
        Map<String,Object> credentialSubjectMap=new HashMap<>();
        mockChallengMap.put("@context",List.of("https://www.w3.org/2018/credentials/examples/v1","https://www.w3.org/2018/credentials/v1"));
        mockChallengMap.put("credentialSubject",credentialSubjectMap);
        Mockito.when(objectMapper.readValue(Mockito.anyString(),Mockito.eq(Map.class))).thenThrow(JsonProcessingException.class);

        Throwable thrownException = Assert.assertThrows(Throwable.class,
                () -> ReflectionTestUtils.invokeMethod(sunbirdRCVCIssuancePlugin, "validateContextUrl", template,  List.of("context1","context2")));


        Assert.assertTrue(thrownException instanceof UndeclaredThrowableException);
        Throwable actualException = ((UndeclaredThrowableException) thrownException).getUndeclaredThrowable();
        Assert.assertTrue(actualException instanceof VCIExchangeException);
        Assert.assertEquals(ErrorConstants.VCI_EXCHANGE_FAILED, actualException.getMessage());
    }


    @Test
    public void getVerifiableCredential_Test(){
        try{
            sunbirdRCVCIssuancePlugin.getVerifiableCredential(null,"holderId",null);
        }catch (VCIExchangeException e){
            Assert.assertEquals(e.getErrorCode(),ErrorConstants.NOT_IMPLEMENTED);
        }
    }
}


