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
    private Map<String, List<Map<String, String>>> dataMap = new HashMap<>();

    @Value("${mosip.certify.data-provider.identifier.column}")
    private String identifierColumn;
    @Value("${mosip.certify.data-provider.fields.include}")
    private String includeFields;

    private Set<String> fieldsToInclude;

    @PostConstruct
    public void init() {
        // Convert comma-separated fields to Set
        // TODO: https://stackoverflow.com/questions/56454902/spring-value-with-arraylist-split-and-obtain-the-first-value
        // We can get all fields in the Set<String> directly
        fieldsToInclude = new HashSet<>(Arrays.asList(includeFields.split(",")));
    }

    public void readCSV(File f) throws IOException {
        try {
            // TODO: Eliminate nested try-catch
            try (FileReader reader = new FileReader(f);
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
                    Map<String, String> rowData = new HashMap<>();
                    // Store only the configured fields
                    for (String header : headers) {
                        if (fieldsToInclude.contains(header) || header.equals(identifierColumn)) {
                            rowData.put(header, record.get(header));
                        }
                    }

                    // Add to dataMap
                    dataMap.computeIfAbsent(identifier, k -> new ArrayList<>()).add(rowData);
                }
            } catch (IOException e) {
                log.error("Error finding csv file path", e);
                throw new IOException("Unable to find the CSV file.");
            }
        } catch (IOException e) {
            log.error("Error fetching csv file from classpath resource", e);
            throw new IOException("Unable to find the classpath resource for csv file.");
        }
    }

    public JSONObject getJsonObjectByIdentifier(String identifier) throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = new JSONObject();
        List<Map<String, String>> records = dataMap.get(identifier);
        if(records == null || records.isEmpty()) {
            log.error("No identifier found.");
            throw new DataProviderExchangeException("No record found in csv with the provided identifier");
        }
        if (records != null && !records.isEmpty()) {
            Map<String, String> record = records.get(0);
            // Add only configured fields to JsonObject
            for (Map.Entry<String, String> entry : record.entrySet()) {
                if (fieldsToInclude.contains(entry.getKey()) || entry.getKey().equals(identifierColumn)) {
                    jsonObject.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return jsonObject;
    }
}
