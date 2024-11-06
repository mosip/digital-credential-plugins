package io.mosip.certify.mockcsvdataprovider.integration;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.mockcsvdataprovider.integration.service.DataProviderService;
import io.mosip.certify.mockcsvdataprovider.integration.service.MockCSVDataProviderPlugin;
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

import java.io.IOException;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MockCSVDataProviderPluginTest {
    @Mock
    DataProviderService dataProviderService;

    @InjectMocks
    MockCSVDataProviderPlugin mockIdaDataProviderPlugin = new MockCSVDataProviderPlugin();

    @Before
    public void setup() throws JSONException, IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("individualId", "1234567");
        jsonObject.put("name", "John Doe");
        jsonObject.put("age", "40");
        jsonObject.put("phone", "98765");

        Mockito.when(dataProviderService.fetchDataFromCSVReader("1234567")).thenReturn(jsonObject);
    }

    @Test
    public void fetchJsonDataWithValidIndividualId_thenPass() throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = mockIdaDataProviderPlugin.fetchData(Map.of("sub", "1234567", "client_id", "CLIENT_ID"));
        Assert.assertNotNull(jsonObject);
        Assert.assertNotNull(jsonObject.get("name"));
        Assert.assertNotNull(jsonObject.get("phone"));
        Assert.assertNotNull(jsonObject.get("age"));
        Assert.assertNotNull(jsonObject.get("individualId"));
        Assert.assertEquals("John Doe", jsonObject.get("name"));
        Assert.assertEquals("98765", jsonObject.get("phone"));
        Assert.assertEquals("40", jsonObject.get("age"));
        Assert.assertEquals("1234567", jsonObject.get("individualId"));
    }

    @Test
    public void fetchJsonDataWithInValidIndividualId_thenFail() {
        try {
            mockIdaDataProviderPlugin.fetchData(Map.of("sub", "12345678", "client_id", "CLIENT_ID"));
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_IDENTITY_DATA", e.getMessage());
        }
    }
}
