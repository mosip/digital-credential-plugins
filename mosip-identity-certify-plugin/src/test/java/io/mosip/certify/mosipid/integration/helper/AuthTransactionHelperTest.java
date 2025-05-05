/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.certify.mosipid.integration.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.mosipid.integration.dto.ClientIdSecretKeyRequest;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class AuthTransactionHelperTest {

    @InjectMocks
    private AuthTransactionHelper authTransactionHelper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RestTemplate restTemplate;

    @Test
    public void getAuthTokenWithValidDetails_thenPass() throws Exception {
        // Set fields via reflection
        ReflectionTestUtils.setField(authTransactionHelper, "authTokenUrl", "https://example.com/token");
        ReflectionTestUtils.setField(authTransactionHelper, "clientId", "test-client");
        ReflectionTestUtils.setField(authTransactionHelper, "secretKey", "test-secret");
        ReflectionTestUtils.setField(authTransactionHelper, "appId", "test-app");

        // Prepare mock request and response
        String dummyRequestJson = "{\"dummy\":\"json\"}";
        ResponseWrapper responseWrapper = new ResponseWrapper();
        HttpHeaders headers = new HttpHeaders();
        headers.put("authorization", Collections.singletonList("Bearer mock-token"));
        ResponseEntity<ResponseWrapper> mockResponse = new ResponseEntity<>(responseWrapper, headers, HttpStatus.OK);

        Mockito.when(objectMapper.writeValueAsString(Mockito.any(RequestWrapper.class))).thenReturn(dummyRequestJson);
        Mockito.when(restTemplate.exchange(
                Mockito.any(RequestEntity.class),
                Mockito.any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        // Act
        String token = authTransactionHelper.getAuthToken();

        // Assert
        Assert.assertEquals("Bearer mock-token", token);
    }

    @Test(expected = Exception.class)
    public void getAuthToken_WhenObjectMapperFails_thenThrowException() throws Exception {
        ReflectionTestUtils.setField(authTransactionHelper, "authTokenUrl", "https://example.com/token");
        ReflectionTestUtils.setField(authTransactionHelper, "clientId", "test-client");
        ReflectionTestUtils.setField(authTransactionHelper, "secretKey", "test-secret");
        ReflectionTestUtils.setField(authTransactionHelper, "appId", "test-app");

        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new RuntimeException("Serialization failed"));

        authTransactionHelper.getAuthToken(); // Should throw
    }

    @Test
    public void purgeAuthTokenCache_thenPass() {
        // Should simply run without exception and log the action
        authTransactionHelper.purgeAuthTokenCache();
    }
}
