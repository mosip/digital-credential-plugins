package io.mosip.certify.mockpostgresdataprovider.integration.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.mockpostgresdataprovider.integration.repository.MockDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockPostgresDataProviderPlugin")
@Component
@Slf4j
public class MockPostgresDataProviderPlugin implements DataProviderPlugin {

    @Autowired
    private MockDataRepository mockDataRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mosip.certify.postgres.data-provider.fields}")
    private String tableFields;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            if (individualId != null) {
                Object[] mockData = mockDataRepository.getIdentityDataFromIndividualId(individualId);
                List<String> includeFields = Arrays.asList(tableFields.split(","));
                JSONObject jsonRes = new JSONObject();
                for(int i=1;i<mockData.length;i++) {
                    jsonRes.put(includeFields.get(i), mockData[i]);
                }
                jsonRes.put("id", "https://piyush7034.github.io/statement.json#StatementCredential");
                log.info("json response: " + jsonRes);
                return jsonRes;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_IDENTITY_DATA");
        }
        throw new DataProviderExchangeException("No Data Found");
    }
}