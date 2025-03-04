package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "MockIdaDataProviderPlugin")
@Component
@Slf4j
public class MockIdaDataProviderPlugin implements DataProviderPlugin {
    @Autowired
    private RestTemplate restTemplate;

    @Value("${mosip.certify.mock.authenticator.get-identity-url}")
    private String getIdentityUrl;

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            String individualId = (String) identityDetails.get("sub");
            if (individualId != null) {
                Map<String, Object> res = restTemplate.getForObject(
                        getIdentityUrl + "/" + individualId,
                        HashMap.class);
                res = (Map<String, Object>) res.get("response");
                JSONObject jsonRes = new JSONObject();
                jsonRes.put("vcVer", "VC-V1");
                jsonRes.put("id", getIdentityUrl + "/" + individualId);
                jsonRes.put("UIN", individualId);
                jsonRes.put("fullName", res.get("fullName"));
                jsonRes.put("gender", res.get("gender"));
                jsonRes.put("dateOfBirth", res.get("dateOfBirth"));
                jsonRes.put("email", res.get("email"));
                jsonRes.put("phone", res.get("phone"));
                jsonRes.put("addressLine1", res.get("streetAddress"));
                jsonRes.put("province", res.get("locality"));
                jsonRes.put("region", res.get("region"));
                jsonRes.put("postalCode", res.get("postalCode"));
                jsonRes.put("face", res.get("encodedPhoto"));
                return jsonRes;
            }
        } catch (Exception e) {
            log.error("Failed to fetch json data for from data provider plugin", e);
            throw new DataProviderExchangeException("ERROR_FETCHING_IDENTITY_DATA");
        }

        throw new DataProviderExchangeException("INVALID_ACCESS_TOKEN");
    }
}