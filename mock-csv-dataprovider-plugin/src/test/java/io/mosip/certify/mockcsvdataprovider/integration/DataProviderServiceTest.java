package io.mosip.certify.mockcsvdataprovider.integration;

import io.mosip.certify.mockcsvdataprovider.integration.service.DataProviderService;
import io.mosip.certify.mockcsvdataprovider.integration.utils.CSVReader;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

@RunWith(MockitoJUnitRunner.class)
public class DataProviderServiceTest {
    @Mock
    CSVReader csvReader;

    @InjectMocks
    DataProviderService dataProviderService = new DataProviderService();

    @Test
    public void fetchDataFromValidFile_thenPass() throws JSONException, IOException {
        dataProviderService.fetchDataFromCSVReader("1234567");
    }
}
