package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import io.mosip.certify.util.CSVReader;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(MockitoJUnitRunner.class)
public class DataProviderServiceTest {
    @Mock
    CSVReader csvReader;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    DataProviderService dataProviderService = new DataProviderService();

    void setup() {
        csvReader = new CSVReader();
    }

    // TODO: Write test for initialize()

    @Test
    public void fetchDataFromValidFile_thenPass() throws JSONException, DataProviderExchangeException {
        JSONObject actual = dataProviderService.fetchDataFromCSVReader("1234567");
        JsonAssertions.assertThatJson(actual).isEqualTo(null);
    }
}
