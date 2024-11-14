package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.util.CSVReader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class DataProviderService {
    @Autowired
    private CSVReader csvReader;
    // TODO: Change the properties value as per convention later
    @Value("${mosip.certify.plugin.csv.file.uri}")
    private String fileReference;
    @Autowired
    private RestTemplate restTemplate;

    /**
     * initialize sets up a CSV data for this DataProviderPlugin on start of application
     * @return
     */
    @PostConstruct
    public File initialize() throws IOException {
        File f;
        if (fileReference.startsWith("http")) {
            // download the file to a path: usecase(docker, spring cloud config)
            f = restTemplate.execute(fileReference, HttpMethod.GET, null, resp -> {
                File ret = File.createTempFile("download", "tmp");
                StreamUtils.copy(resp.getBody(), new FileOutputStream(ret));
                return ret;
            });
        } else if (fileReference.startsWith("classpath:")) {
            try {
                // usecase(local setup)
                f = ResourceUtils.getFile(fileReference);
            } catch (IOException e) {
                throw new FileNotFoundException("File not found in the classpath: " + fileReference);
            }
        } else {
            // usecase(local setup)
            f = new File(fileReference);
            if (!f.isFile()) {
                // TODO: make sure it crashes the application
                throw new FileNotFoundException("File not found: " + fileReference);
            }
        }
        csvReader.readCSV(f);
        return f;
    }

    public JSONObject fetchDataFromCSVReader(String identifier) throws JSONException, DataProviderExchangeException {
        JSONObject result = csvReader.getJsonObjectByIdentifier(identifier);
        return result;
    }
}
