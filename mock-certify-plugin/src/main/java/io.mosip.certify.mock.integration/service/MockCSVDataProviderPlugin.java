package io.mosip.certify.mock.integration.service;


import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.util.CSVReader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockCSVDataProviderPlugin")
@Component
@Slf4j
public class MockCSVDataProviderPlugin implements DataProviderPlugin {
    @Value("${mosip.certify.mock.vciplugin.id-uri:https://example.com/}")
    private String id;
    @Autowired
    private CSVReader csvReader;
    @Value("${mosip.certify.mock.data-provider.csv-registry-uri:}")
    private String csvRegistryURI;
    @Value("${mosip.certify.mock.data-provider.csv.identifier-column:}")
    private String identifierColumn;
    @Value("#{'${mosip.certify.mock.data-provider.csv.data-columns:}'.split(',')}")
    private Set<String> dataColumns;
    @Autowired
    private RestTemplate restTemplate;

    /**
     * initialize sets up a CSV data for this DataProviderPlugin on start of application
     * @return
     */
    @PostConstruct
    public void initialize() throws IOException, JSONException {
        try {
            File filePath;
            if (csvRegistryURI.startsWith("http")) {
                // download the file to a path: usecase(docker, spring cloud config)
                filePath = restTemplate.execute(csvRegistryURI, HttpMethod.GET, null, resp -> {
                    File ret = File.createTempFile("download", "tmp");
                    StreamUtils.copy(resp.getBody(), new FileOutputStream(ret));
                    return ret;
                });
            } else if (csvRegistryURI.startsWith("classpath:")) {
                try {
                    // usecase(local setup)
                    filePath = ResourceUtils.getFile(csvRegistryURI);
                } catch (IOException e) {
                    throw new FileNotFoundException("File not found in: " + csvRegistryURI);
                }
            } else {
                // usecase(local setup)
                filePath = new File(csvRegistryURI);
                if (!filePath.isFile()) {
                    // TODO: make sure it crashes the application
                    throw new FileNotFoundException("File not found: " + csvRegistryURI);
                }
            }
            csvReader.readCSV(filePath, identifierColumn, dataColumns);
        } catch (Exception e) {
            log.error("Undefined CSV source. " + e.getMessage());
        }
    }

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            if (individualId != null) {
                JSONObject jsonRes = csvReader.getJsonObjectByIdentifier(individualId);
                return jsonRes;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_IDENTITY_DATA");
        }
        throw new DataProviderExchangeException("No Data Found");
    }
}
