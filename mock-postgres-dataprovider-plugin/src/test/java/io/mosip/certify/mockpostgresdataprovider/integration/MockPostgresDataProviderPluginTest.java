package io.mosip.certify.mockpostgresdataprovider.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.mockpostgresdataprovider.integration.repository.MockDataRepository;
import io.mosip.certify.mockpostgresdataprovider.integration.service.MockPostgresDataProviderPlugin;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MockPostgresDataProviderPluginTest {
    @Mock
    MockDataRepository mockDataRepository;

    @InjectMocks
    MockPostgresDataProviderPlugin mockPostgresDataProviderPlugin = new MockPostgresDataProviderPlugin();

    @Before
    public void setup() {
        ReflectionTestUtils.setField(mockPostgresDataProviderPlugin, "tableFields", List.of("individualId", "name", "dateOfBirth", "phoneNumber", "email","landArea"));
        Object[] obj = new Object[]{"1234567", "John Doe", "01/01/1980", "012345", "john@test.com", 100.24};
        Mockito.when(mockDataRepository.getIdentityDataFromIndividualId("1234567")).thenReturn(obj);
    }

    @Test
    public void fetchJsonDataWithValidIndividualId_thenPass() throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = mockPostgresDataProviderPlugin.fetchData(Map.of("sub", "1234567", "client_id", "CLIENT_ID"));
        Assert.assertNotNull(jsonObject);
        Assert.assertNotNull(jsonObject.get("name"));
        Assert.assertNotNull(jsonObject.get("dateOfBirth"));
        Assert.assertNotNull(jsonObject.get("phoneNumber"));
        Assert.assertNotNull(jsonObject.get("email"));
        Assert.assertNotNull(jsonObject.get("landArea"));
        Assert.assertEquals("John Doe", jsonObject.get("name"));
        Assert.assertEquals("01/01/1980", jsonObject.get("dateOfBirth"));
        Assert.assertEquals("012345", jsonObject.get("phoneNumber"));
        Assert.assertEquals("john@test.com", jsonObject.get("email"));
        Assert.assertEquals(100.24, jsonObject.get("landArea"));
    }

    @Test
    public void fetchJsonDataWithInValidIndividualId_thenFail() throws DataProviderExchangeException, JSONException {
        try {
            mockPostgresDataProviderPlugin.fetchData(Map.of("sub", "12345678", "client_id", "CLIENT_ID"));
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_IDENTITY_DATA", e.getMessage());
        }
    }
}