package io.mosip.certify.util;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Component
@Slf4j
public class CSVReader {
    private Map<String, JSONObject> dataMap = new HashMap<>();

    public void readCSV(File filePath, String identifierColumn, Set<String> dataColumns) throws IOException, JSONException {
        try (FileReader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            // Get header names
            List<String> headers = csvParser.getHeaderNames();
            // Validate that identifier column exists
            if (!headers.contains(identifierColumn)) {
                throw new IllegalArgumentException("Identifier column " + identifierColumn + " not found in CSV");
            }

            // Process each record
            for (CSVRecord record : csvParser) {
                String identifier = record.get(identifierColumn);
                JSONObject jsonObject = new JSONObject();
                // Store only the configured fields
                for (String header : headers) {
                    if (dataColumns.contains(header) || header.equals(identifierColumn)) {
                        jsonObject.put(header, record.get(header));
                    }
                }

                // Add to dataMap
                dataMap.put(identifier, jsonObject);
            }
        } catch (IOException e) {
            log.error("Error finding csv file path", e);
            throw new IOException("Unable to find the CSV file.");
        }
    }

    public JSONObject getJsonObjectByIdentifier(String identifier) throws DataProviderExchangeException, JSONException {
        JSONObject record = dataMap.get(identifier);
        if(record == null) {
            log.error("No identifier found.");
            throw new DataProviderExchangeException("No record found in csv with the provided identifier");
        }

        return record;
    }
}
