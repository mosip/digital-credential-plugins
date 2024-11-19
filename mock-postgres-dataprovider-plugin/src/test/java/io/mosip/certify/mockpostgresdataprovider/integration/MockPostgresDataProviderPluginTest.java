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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MockPostgresDataProviderPluginTest {
    @Mock
    MockDataRepository mockDataRepository;

    @InjectMocks
    MockPostgresDataProviderPlugin mockIdaDataProviderPlugin = new MockPostgresDataProviderPlugin();

    @Before
    public void setup() {
        String str = """
                {
                    "keyOne": "valueOne",
                    "keyTwo": "valueTwo"
                }
                """;
        Object[] obj = new Object[]{"1234567", str};

        Mockito.when(mockDataRepository.getIdentityDataFromIndividualId("1234567")).thenReturn(obj);
    }

    @Test
    public void fetchJsonDataWithValidIndividualId_thenPass() throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = mockIdaDataProviderPlugin.fetchData(Map.of("sub", "1234567", "client_id", "CLIENT_ID"));
        Assert.assertNotNull(jsonObject);
        Assert.assertNotNull(jsonObject.get("keyOne"));
        Assert.assertNotNull(jsonObject.get("keyTwo"));
        Assert.assertEquals("https://piyush7034.github.io/statement.json#StatementCredential", jsonObject.get("id"));
    }

    @Test
    public void fetchJsonDataWithInValidIndividualId_thenFail() throws DataProviderExchangeException, JSONException {
        try {
            mockIdaDataProviderPlugin.fetchData(Map.of("sub", "12345678", "client_id", "CLIENT_ID"));
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_IDENTITY_DATA", e.getMessage());
        }
    }
}