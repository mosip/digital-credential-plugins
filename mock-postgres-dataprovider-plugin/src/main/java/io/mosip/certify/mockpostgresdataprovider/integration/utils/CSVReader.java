package io.mosip.certify.mockpostgresdataprovider.integration.utils;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser; import org.apache.commons.csv.CSVRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component; import java.io.FileReader;
import java.io.IOException; import java.util.*;

@Component
public class CSVReader {
    private Map<String, List<Map<String, String>>> dataMap = new HashMap<>();

    @Value("${csv.identifier.column}")
    private String identifierColumn;
    @Value("${csv.fields.include}")
    private String includeFields;

    private Set<String> fieldsToInclude;

    @PostConstruct
    public void init() {
        // Convert comma-separated fields to
        fieldsToInclude = new HashSet<>(Arrays.asList(includeFields.split(",")));
    }

    public void readCSV(String filePath) throws IOException {
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
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    public JSONObject getJsonObjectByIdentifier(String identifier) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        List<Map<String, String>> records = dataMap.get(identifier);
        if (records != null && !records.isEmpty()) {
            Map<String, String> record = records.get(0);
            // Add only configured fields to JsonObject
            for (Map.Entry<String, String> entry : record.entrySet()) {
                if (fieldsToInclude.contains(entry.getKey()) || entry.getKey().equals(identifierColumn)) {
                    jsonObject.put(entry.getKey(), entry.getValue());
                }
            }
        } return jsonObject;
    }
}
