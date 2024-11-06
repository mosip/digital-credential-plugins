package io.mosip.certify.mockcsvdataprovider.integration.service;

import io.mosip.certify.mockcsvdataprovider.integration.utils.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class DataProviderService {
    @Autowired
    private CSVReader csvReader;

    public JSONObject fetchDataFromCSVReader(String identifier) throws JSONException, IOException {
        JSONObject result;
        try {
            csvReader.readCSV("farmer_identity_data.csv");
            result = csvReader.getJsonObjectByIdentifier(identifier);
        } catch (IOException e) {
            throw new IOException(e);
        }

        return result;
    }
}