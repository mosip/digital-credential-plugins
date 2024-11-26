package io.mosip.certify.postgresdataprovider.integration.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.postgresdataprovider.integration.repository.DataProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "PostgresDataProviderPlugin")
@Component
@Slf4j
public class PostgresDataProviderPlugin implements DataProviderPlugin {

    @Autowired
    private DataProviderRepository dataProviderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("#{${mosip.certify.data-provider-plugin.postgres.scope-query-mapping}}")
    private LinkedHashMap<String, String> scopeQueryMapping;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            String scope = (String) identityDetails.get("scope");
            String queryString = scopeQueryMapping.get(scope);
            if (individualId != null) {
                Map<String, Object> dataRecord = dataProviderRepository.fetchQueryResult(individualId,
                            queryString);
                JSONObject jsonResponse = new JSONObject(dataRecord);
                return jsonResponse;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_DATA_RECORD_FROM_TABLE");
        }
        throw new DataProviderExchangeException("No Data Found");
    }
}