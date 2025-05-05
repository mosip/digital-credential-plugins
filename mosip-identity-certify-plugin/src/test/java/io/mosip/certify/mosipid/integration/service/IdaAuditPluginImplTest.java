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
}
