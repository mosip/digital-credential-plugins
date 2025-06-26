package io.mosip.certify.mosipid.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.dto.AuditDTO;
import io.mosip.certify.api.util.Action;
import io.mosip.certify.api.util.ActionStatus;
import io.mosip.certify.mosipid.integration.dto.AuditResponse;
import io.mosip.certify.mosipid.integration.helper.AuthTransactionHelper;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class IdaAuditPluginImplTest {

    @InjectMocks
    private IdaAuditPluginImpl auditPlugin;

    @Mock
    private AuthTransactionHelper authTransactionHelper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate restTemplate;

    private AuditDTO auditDTO;

    @Before
    public void setUp() {
        auditDTO = new AuditDTO();
        auditDTO.setIdType("UIN");
        auditDTO.setTransactionId("txn123");

        ReflectionTestUtils.setField(auditPlugin, "auditManagerUrl", "http://localhost/audit");
        ReflectionTestUtils.setField(auditPlugin, "auditDescriptionMaxLength", 100);
    }

    @Test
    public void logAudit_withoutThrowable_success() throws Exception {
        String authToken = "Bearer test-token";
        when(authTransactionHelper.getAuthToken()).thenReturn(authToken);

        ResponseWrapper<AuditResponse> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setErrors(null);
        ResponseEntity<ResponseWrapper> response = new ResponseEntity<>(responseWrapper, HttpStatus.OK);

        when(objectMapper.writeValueAsString(Mockito.any(RequestWrapper.class))).thenReturn("{}");
        when(restTemplate.exchange(Mockito.any(RequestEntity.class),
                Mockito.<ParameterizedTypeReference<ResponseWrapper>>any()))
                .thenReturn(response);

        auditPlugin.logAudit("test-user", Action.VC_ISSUANCE, ActionStatus.SUCCESS, auditDTO, null);

        verify(restTemplate, times(1)).exchange(Mockito.any(), Mockito.any(ParameterizedTypeReference.class));
    }

    @Test
    public void logAudit_withThrowable_success() throws Exception {
        when(authTransactionHelper.getAuthToken()).thenReturn("token");

        ResponseWrapper<AuditResponse> responseWrapper = new ResponseWrapper<>();
        ResponseEntity<ResponseWrapper> response = new ResponseEntity<>(responseWrapper, HttpStatus.OK);

        when(objectMapper.writeValueAsString(Mockito.any(RequestWrapper.class))).thenReturn("{}");
        when(restTemplate.exchange(Mockito.any(RequestEntity.class),
                Mockito.<ParameterizedTypeReference<ResponseWrapper>>any()))
                .thenReturn(response);

        Throwable exception = new RuntimeException("Test exception");
        auditPlugin.logAudit("test-user", Action.VC_ISSUANCE, ActionStatus.ERROR, auditDTO, exception);
    }

    @Test
    public void logAudit_withLongDescription_trimmed() throws Exception {
        when(authTransactionHelper.getAuthToken()).thenReturn("token");

        ResponseWrapper<AuditResponse> responseWrapper = new ResponseWrapper<>();
        ResponseEntity<ResponseWrapper> response = new ResponseEntity<>(responseWrapper, HttpStatus.OK);

        when(objectMapper.writeValueAsString(Mockito.any(RequestWrapper.class))).thenReturn("{}");
        when(restTemplate.exchange(Mockito.any(RequestEntity.class),
                Mockito.<ParameterizedTypeReference<ResponseWrapper>>any()))
                .thenReturn(response);

        auditDTO.setTransactionId("x".repeat(200));  // long ID to inflate description
        auditPlugin.logAudit("test-user", Action.VC_ISSUANCE, ActionStatus.SUCCESS, auditDTO, null);
    }

    @Test
    public void logAudit_withUnauthorized_shouldPurgeCache() throws Exception {
        when(authTransactionHelper.getAuthToken()).thenReturn("token");

        ResponseEntity<ResponseWrapper> response = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        when(objectMapper.writeValueAsString(Mockito.any(RequestWrapper.class))).thenReturn("{}");
        when(restTemplate.exchange(Mockito.any(RequestEntity.class),
                Mockito.<ParameterizedTypeReference<ResponseWrapper>>any()))
                .thenReturn(response);

        auditPlugin.logAudit("test-user", Action.VC_ISSUANCE, ActionStatus.SUCCESS, auditDTO, null);

        verify(authTransactionHelper).purgeAuthTokenCache();
    }

    @Test
    public void logAudit_withForbidden_shouldPurgeCache() throws Exception {
        when(authTransactionHelper.getAuthToken()).thenReturn("token");

        ResponseEntity<ResponseWrapper> response = new ResponseEntity<>(HttpStatus.FORBIDDEN);
        when(objectMapper.writeValueAsString(Mockito.any(RequestWrapper.class))).thenReturn("{}");
        when(restTemplate.exchange(Mockito.any(RequestEntity.class),
                Mockito.<ParameterizedTypeReference<ResponseWrapper>>any()))
                .thenReturn(response);

        auditPlugin.logAudit("test-user", Action.VC_ISSUANCE, ActionStatus.SUCCESS, auditDTO, null);

        verify(authTransactionHelper).purgeAuthTokenCache();
    }

    @Test
    public void logAudit_whenExceptionOccurs_shouldCatch() throws Exception {
        when(authTransactionHelper.getAuthToken()).thenThrow(new RuntimeException("mock error"));

        auditPlugin.logAudit("test-user", Action.VC_ISSUANCE, ActionStatus.SUCCESS, auditDTO, null);
        // Should not throw
    }

    @Test
    public void getAuditDescription_shouldReturnValidJson() throws Exception {
        String json = ReflectionTestUtils.invokeMethod(auditPlugin, "getAuditDescription", auditDTO);
        JSONObject obj = new JSONObject(json);
        assert obj.getString("transactionId").equals("txn123");
        assert obj.getString("idType").equals("UIN");
    }

    // Add inside IdaAuditPluginImplTest

    @Test
    public void auditRequest_gettersSetters_allArgsConstructor() {
        LocalDateTime localDateTime = LocalDateTime.now();
        io.mosip.certify.mosipid.integration.dto.AuditRequest req1 = new io.mosip.certify.mosipid.integration.dto.AuditRequest(
                "eventId", "eventName", "eventType", localDateTime, "hostName", "hostIp",
                "appId", "appName", "sessionUserId", "sessionUserName", "id", "idType",
                "createdBy", "moduleName", "moduleId", "desc"
        );
        assertEquals("eventId", req1.getEventId());
        assertEquals("eventName", req1.getEventName());
        assertEquals("eventType", req1.getEventType());
        assertEquals(localDateTime, req1.getActionTimeStamp());
        assertEquals("hostName", req1.getHostName());
        assertEquals("hostIp", req1.getHostIp());
        assertEquals("appId", req1.getApplicationId());
        assertEquals("appName", req1.getApplicationName());
        assertEquals("sessionUserId", req1.getSessionUserId());
        assertEquals("sessionUserName", req1.getSessionUserName());
        assertEquals("idType", req1.getIdType());
        assertEquals("createdBy", req1.getCreatedBy());
        assertEquals("moduleName", req1.getModuleName());
        assertEquals("moduleId", req1.getModuleId());
        assertEquals("desc", req1.getDescription());
        assertEquals("id", req1.getId());

        io.mosip.certify.mosipid.integration.dto.AuditRequest req2 = new io.mosip.certify.mosipid.integration.dto.AuditRequest();
        req2.setEventId("eventId");
        req2.setEventName("eventName");
        req2.setEventType("eventType");
        req2.setActionTimeStamp(localDateTime);
        req2.setHostName("hostName");
        req2.setHostIp("hostIp");
        req2.setApplicationId("appId");
        req2.setApplicationName("appName");
        req2.setSessionUserId("sessionUserId");
        req2.setSessionUserName("sessionUserName");
        req2.setIdType("idType");
        req2.setCreatedBy("createdBy");
        req2.setModuleName("moduleName");
        req2.setModuleId("moduleId");
        req2.setDescription("desc");
        req2.setId("id");

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
        assertTrue(req1.toString().contains("eventId"));
    }

    @Test
    public void auditResponse_gettersSetters_allArgsConstructor() {
        AuditResponse resp1 = new AuditResponse();
        resp1.setStatus(true);

        AuditResponse resp2 = new AuditResponse();
        resp2.setStatus(true);

        assertEquals(resp1, resp2);
        assertEquals(resp1.hashCode(), resp2.hashCode());
        assertEquals(resp1.toString(), resp2.toString());
    }
}
