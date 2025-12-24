package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.util.CSVReader;
import io.swagger.models.HttpMethod;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MockCSVDataProviderPluginTest {
    @Mock
    CSVReader csvReader;

    @Mock
    RestTemplate restTemplate;

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

        when(csvReader.getJsonObjectByIdentifier("1234567")).thenReturn(jsonObject);
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

    @Test
    public void fetchJsonDataWithoutSubKey_thenFail() {
        try {
            mockCSVDataProviderPlugin.fetchData(Map.of("client_id", "CLIENT_ID"));
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_IDENTITY_DATA -> Check the individualId or data source", e.getMessage());
        }
    }

    @Test
    public void fetchData_whenCsvReaderThrowsException_shouldThrowDataProviderExchangeException() throws Exception {
        // Arrange
        String individualId = "1234567";
        when(csvReader.getJsonObjectByIdentifier(individualId))
                .thenThrow(new DataProviderExchangeException("Simulated CSV read error"));

        // Act & Assert
        try {
            mockCSVDataProviderPlugin.fetchData(Map.of("sub", individualId));
            fail("Expected DataProviderExchangeException to be thrown");
        } catch (DataProviderExchangeException e) {
            // Verify the exception
            assertEquals("Simulated CSV read error", e.getMessage());

            // Optional: Verify logging was called
//            verify(log).error(anyString(), any(RuntimeException.class));
        }
    }

    @Test
    public void initialize_withClasspathUri_shouldReadFile() throws Exception {
        String path = "classpath:test.csv";
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "csvRegistryURI", path);

        File file = new File("src/test/resources/test.csv");
        Assert.assertTrue(file.exists());

        File result = mockCSVDataProviderPlugin.initialize();
        Assert.assertNotNull(result);
    }

    @Test(expected = FileNotFoundException.class)
    public void initialize_withInvalidFile_shouldThrowException() throws Exception {
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "csvRegistryURI", "/non/existing/file.csv");
        mockCSVDataProviderPlugin.initialize();
    }

    @Test
    public void initialize_withLocalPath_shouldReadFile() throws Exception {
        File file = new File("src/test/resources/test.csv");
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "csvRegistryURI", file.getAbsolutePath());
        File result = mockCSVDataProviderPlugin.initialize();
        Assert.assertTrue(result.exists());
    }

    @Test
    public void initialize_withHttpCsvRegistryURI_shouldDownloadFile() throws Exception {
        // Set up fields
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "csvRegistryURI", "http://mock-url.com/test.csv");
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "identifierColumn", "individualId");
        ReflectionTestUtils.setField(mockCSVDataProviderPlugin, "dataColumns", Set.of("name", "age"));

        // Prepare dummy file
        File dummyFile = File.createTempFile("test_csv", ".csv");
        dummyFile.deleteOnExit();

        // Mock the execute() method with proper type parameters
        when(restTemplate.execute(
                eq("http://mock-url.com/test.csv"),
                 any(),
                any(),
                any(ResponseExtractor.class)
        )).thenAnswer(invocation -> {
            // Get the ResponseExtractor (the lambda from the actual code)
            ResponseExtractor<File> extractor = invocation.getArgument(3);

            // Create a mock response
            ClientHttpResponse response = mock(ClientHttpResponse.class);
            when(response.getBody()).thenReturn(new FileInputStream(dummyFile));

            // Execute the extractor with our mock response
            return extractor.extractData(response);
        });

        // Run initialize
        File result = mockCSVDataProviderPlugin.initialize();

        // Validate
        assertNotNull(result);
        assertTrue(result.exists());

        // Verify CSVReader was called with the downloaded file
        verify(csvReader).readCSV(
                eq(result),
                eq("individualId"),
                eq(Set.of("name", "age"))
        );
    }
}
