package io.mosip.certify.util;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import lombok.SneakyThrows;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class CSVReaderTest {
    @InjectMocks
    CSVReader csvReader;

    Set<String> dataColumns;
    Map<String, JSONObject> dataMap = new HashMap<>();

    @Before
    public void setup() {
        String dataColumnFields = "name,age,phone";
        dataColumns = new HashSet<>(Arrays.asList(dataColumnFields.split(",")));
        ReflectionTestUtils.setField(csvReader, "dataMap", dataMap);
    }

    @Test
    public void readCSVDataFromValidFile_thenPass() throws IOException, JSONException {
        File f = new File("src/test/resources/test.csv");
        csvReader.readCSV(f, "individualId", dataColumns);
        Assert.assertNotNull(dataMap);
        Assert.assertNotNull(dataMap.get("1234567"));
        JSONObject jsonObject = dataMap.get("1234567");
        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("John Doe", jsonObject.get("name"));
        Assert.assertEquals("9876543210", jsonObject.get("phone"));
        Assert.assertEquals("40", jsonObject.get("age"));
    }

    @Test
    public void readCSVDataFromInvalidFile_thenFail() throws JSONException {
        try {
            File f = new File("test.csv");
            csvReader.readCSV(f, "individualId", dataColumns);
        } catch (IOException e) {
            Assert.assertEquals("Unable to find the CSV file.", e.getMessage());
        }
    }

    @SneakyThrows
    @Test
    public void getJsonObjectByValidIdentifier_thenPass() {
        Map<String, JSONObject> data = new HashMap<>();
        JSONObject jsonObject = new JSONObject(Map.of("phone", "9876543210", "name", "John Doe", "individualId", "1234567", "age", "40"));
        data.put("1234567", jsonObject);
        ReflectionTestUtils.setField(csvReader, "dataMap", data);

        JSONObject jsonObjectResult = csvReader.getJsonObjectByIdentifier("1234567");
        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("1234567", jsonObjectResult.get("individualId"));
        Assert.assertEquals("John Doe", jsonObjectResult.get("name"));
        Assert.assertEquals("40", jsonObjectResult.get("age"));
        Assert.assertEquals("9876543210", jsonObjectResult.get("phone"));
    }

    @Test
    public void getJsonObjectByInvalidIdentifier_thenFail() throws JSONException {
        Map<String, JSONObject> data = new HashMap<>();
        JSONObject jsonObject = new JSONObject(Map.of("phone", "9876543210", "name", "John Doe", "individualId", "1234567", "age", "40"));
        data.put("1234567", jsonObject);
        ReflectionTestUtils.setField(csvReader, "dataMap", data);
        try {
            csvReader.getJsonObjectByIdentifier("12345678");
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("No record found in csv with the provided identifier", e.getMessage());
        }
    }
}
