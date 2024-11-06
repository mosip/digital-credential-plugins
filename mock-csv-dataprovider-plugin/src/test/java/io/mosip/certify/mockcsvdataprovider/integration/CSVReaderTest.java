package io.mosip.certify.mockcsvdataprovider.integration;

import io.mosip.certify.mockcsvdataprovider.integration.utils.CSVReader;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class CSVReaderTest {
    @InjectMocks
    CSVReader csvReader;

    Set<String> fieldsToInclude;
    Map<String, List<Map<String, String>>> dataMap = new HashMap<>();

    @Before
    public void setup() {
        String includeFields = "name,age,phone";
        ReflectionTestUtils.setField(csvReader, "identifierColumn", "individualid");
        ReflectionTestUtils.setField(csvReader, "includeFields", includeFields);

        fieldsToInclude = new HashSet<>(Arrays.asList(includeFields.split(",")));
        ReflectionTestUtils.setField(csvReader, "fieldsToInclude", fieldsToInclude);
        ReflectionTestUtils.setField(csvReader, "dataMap", dataMap);
    }

    @Test
    public void readCSVDataFromValidFile_thenPass() throws IOException {
        csvReader.readCSV("test.csv");
        Assert.assertNotNull(dataMap);
        Assert.assertNotNull(dataMap.get("1234567"));
        Assert.assertNotNull(dataMap.get("2345678"));
    }

    @Test
    public void readCSVDataFromInvalidFile_thenFail() {
        try {
            csvReader.readCSV("test_id.csv");
        } catch (IOException e) {
            Assert.assertEquals("Unable to find the classpath resource for csv file.", e.getMessage());
        }
    }

    @Test
    public void getJsonObjectByValidIdentifier_thenPass() throws JSONException {
        Map<String, List<Map<String, String>>> data = new HashMap<>();
        data.put("1234567", List.of(Map.of("individualid", "1234567", "name", "test", "age", "40", "phone", "98765")));
        ReflectionTestUtils.setField(csvReader, "dataMap", data);

        JSONObject jsonObject = csvReader.getJsonObjectByIdentifier("1234567");
        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("1234567", jsonObject.get("individualid"));
        Assert.assertEquals("test", jsonObject.get("name"));
        Assert.assertEquals("40", jsonObject.get("age"));
        Assert.assertEquals("98765", jsonObject.get("phone"));
    }

    @Test
    public void getJsonObjectByInvalidIdentifier_thenFail() throws JSONException {
        Map<String, List<Map<String, String>>> data = new HashMap<>();
        data.put("1234567", List.of(Map.of("individualid", "1234567", "name", "test", "age", "40", "phone", "98765")));
        ReflectionTestUtils.setField(csvReader, "dataMap", data);

        try {
            csvReader.getJsonObjectByIdentifier("12345678");
        } catch (RuntimeException e) {
            Assert.assertEquals("No record found in csv with the provided identifier", e.getMessage());
        }
    }
}
