package io.mosip.certify.mock.integration.service;


import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockCSVDataProviderPlugin")
@Component
@Slf4j
public class MockCSVDataProviderPlugin implements DataProviderPlugin {
    @Autowired
    private DataProviderService dataService;
    @Value("${mosip.certify.mock.vciplugin.id-uri:https://example.com/}")
    private String id;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            if (individualId != null) {
                JSONObject jsonRes;
                jsonRes = dataService.fetchDataFromCSVReader(individualId);
                jsonRes.put("id", id + individualId);
                return jsonRes;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_IDENTITY_DATA");
        }
        throw new DataProviderExchangeException("No Data Found");
    }
}
