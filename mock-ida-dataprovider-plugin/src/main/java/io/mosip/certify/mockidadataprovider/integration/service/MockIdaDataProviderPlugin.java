package io.mosip.certify.mockidadataprovider.integration.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.mockidadataprovider.integration.repository.MockDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockIdaDataProviderPlugin")
@Component
@Slf4j
public class MockIdaDataProviderPlugin implements DataProviderPlugin {

    @Autowired
    private MockDataRepository mockDataRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            if (individualId != null) {
                Object[] mockData = mockDataRepository.getIdentityDataFromIndividualId(individualId);
                Map<String, Object> mockDataMap = new HashMap<>();
                try {
                    mockDataMap = objectMapper.readValue(mockData[3].toString(), HashMap.class);
                    log.info("mock data map " + mockDataMap);
                } catch (Exception e) {
                    log.error("mock data not present");
                }
                JSONObject jsonRes = new JSONObject(mockDataMap);
                jsonRes.put("name", mockData[0].toString());
                jsonRes.put("phoneNumber", mockData[1].toString());
                jsonRes.put("dateOfBirth", mockData[2].toString());
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