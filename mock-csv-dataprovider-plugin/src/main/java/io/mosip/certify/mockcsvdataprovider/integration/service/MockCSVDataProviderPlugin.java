package io.mosip.certify.mockcsvdataprovider.integration.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockPostgresDataProviderPlugin")
@Component
@Slf4j
public class MockCSVDataProviderPlugin implements DataProviderPlugin {
    @Autowired
    private DataProviderService dataService;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            if (individualId != null) {
                JSONObject jsonRes;
                jsonRes = dataService.fetchDataFromCSVReader(individualId);
                jsonRes.put("id", "https://vharsh.github.io/farmer.json#FarmerProfileCredential");
                return jsonRes;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_IDENTITY_DATA");
        }
        throw new DataProviderExchangeException("No Data Found");
    }
}