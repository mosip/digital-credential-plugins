package io.mosip.certify.mockidadataprovider.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.mockidadataprovider.integration.repository.MockDataRepository;
import io.mosip.certify.mockidadataprovider.integration.repository.MockDataRepositoryImpl;
import io.mosip.certify.mockidadataprovider.integration.service.MockIdaDataProviderPlugin;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MockIdaDataProviderPluginTest {
    @Mock
    MockDataRepository mockDataRepository;

    @InjectMocks
    MockIdaDataProviderPlugin mockIdaDataProviderPlugin = new MockIdaDataProviderPlugin();

    @Before
    public void setup() {
        Map<String, Object> identityMap = Map.of("highestEducation", "Bachelor's Degree",
                "typeOfHouse", "Ranch", "farmingTypes", List.of("Organic", "Mixed"));
        JSONObject jsonObject = new JSONObject(identityMap);
        Object[] obj = new Object[]{"John Doe", "987656789", "01-01-1980", jsonObject};

        Mockito.when(mockDataRepository.getIdentityDataFromIndividualId("1234567")).thenReturn(obj);
    }

    @Test
    public void fetchJsonDataWithValidIndividualId_thenPass() throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = mockIdaDataProviderPlugin.fetchData(Map.of("sub", "1234567", "client_id", "CLIENT_ID"));
        Assert.assertNotNull(jsonObject);
        Assert.assertNotNull(jsonObject.get("name"));
        Assert.assertNotNull(jsonObject.get("phoneNumber"));
        Assert.assertNotNull(jsonObject.get("dateOfBirth"));
        Assert.assertNotNull(jsonObject.get("id"));
        Assert.assertEquals("John Doe", jsonObject.get("name"));
        Assert.assertEquals("987656789", jsonObject.get("phoneNumber"));
        Assert.assertEquals("01-01-1980", jsonObject.get("dateOfBirth"));
        Assert.assertEquals("https://vharsh.github.io/farmer.json#FarmerProfileCredential", jsonObject.get("id"));
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
