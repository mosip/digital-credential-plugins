package io.mosip.certify.mockpostgresdataprovider.integration.service;

import io.mosip.certify.mockpostgresdataprovider.integration.utils.CSVReader;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DataService {
    @Autowired
    private CSVReader csvReader;

    public JSONObject processData(String id) throws JSONException, IOException {

           String filePath = new ClassPathResource("farmer_identity_data.csv").getFile().getPath();

        csvReader.readCSV(filePath);
        JSONObject result = csvReader.getJsonObjectByIdentifier(id);
        System.out.println(result.toString());
        return result;
    }
}