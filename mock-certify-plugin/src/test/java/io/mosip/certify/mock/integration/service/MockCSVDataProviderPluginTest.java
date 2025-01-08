package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.util.CSVReader;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class MockCSVDataProviderPluginTest {
    @Mock
    CSVReader csvReader;

    @InjectMocks
    MockCSVDataProviderPlugin mockCSVDataProviderPlugin = new MockCSVDataProviderPlugin();

    @Before
    public void setup() throws JSONException, DataProviderExchangeException {
        String dataColumnFields = "name,age,phone";
        Set<String> dataColumns = new HashSet<>(Arrays.asList(dataColumnFields.split(",")));
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "identifierColumn", "individualId");
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "dataColumns", dataColumns);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("individualId", "1234567");
        jsonObject.put("name", "John Doe");
        jsonObject.put("age", "40");
        jsonObject.put("phone", "98765");

        Mockito.when(csvReader.getJsonObjectByIdentifier("1234567")).thenReturn(jsonObject);
    }

    @Test
    public void fetchJsonDataWithValidIndividualId_thenPass() throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = mockCSVDataProviderPlugin.fetchData(Map.of("sub", "1234567", "client_id", "CLIENT_ID"));
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
            mockCSVDataProviderPlugin.fetchData(Map.of("sub", "12345678", "client_id", "CLIENT_ID"));
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_IDENTITY_DATA", e.getMessage());
        }
    }
}
