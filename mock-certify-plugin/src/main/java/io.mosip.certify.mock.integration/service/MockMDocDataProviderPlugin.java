package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@ConditionalOnProperty(
        value = "mosip.certify.integration.data-provider-plugin",
        havingValue = "MockMDocDataProviderPlugin"
)
@Component
@Slf4j
public class MockMDocDataProviderPlugin implements DataProviderPlugin {

    @Value("${mosip.certify.mock.data-provider.mdoc.issuer-id:https://example-issuer.org}")
    private String issuer;

    @Value("${mosip.certify.mock.data-provider.mdoc.default-doctype:org.iso.18013.5.1.mDL}")
    private String defaultDocType;

    @Value("${mosip.certify.mock.data-provider.mdoc.validity-days:365}")
    private long validityDays;

    @PostConstruct
    public void init() {
        log.info("MockMDocDataProviderPlugin initialized with issuer: {}, defaultDocType: {}, validityDays: {}",
                issuer, defaultDocType, validityDays);
    }

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        try {
            if (identityDetails == null || identityDetails.isEmpty()) {
                throw new DataProviderExchangeException("No identity data provided.");
            }

            Map<String, Object> claims = (Map<String, Object>) identityDetails.get("claims");

            if (claims == null || claims.isEmpty()) {
                throw new DataProviderExchangeException("Claims section is missing or empty.");
            }

            // Take the first (and assumed only) namespace in claims
            String namespace = claims.keySet().iterator().next();

            Map<String, Object> namespaceClaims = (Map<String, Object>) claims.get(namespace);

            if (namespaceClaims == null || namespaceClaims.isEmpty()) {
                throw new DataProviderExchangeException("No claim data found under namespace: " + namespace);
            }

            JSONObject claimData = new JSONObject(namespaceClaims);

            // Add system metadata
            claimData.put("_docType", identityDetails.getOrDefault("doctype", defaultDocType));
            claimData.put("_issuer", issuer);
            claimData.put("_issuedAt", Instant.now().toString());
            claimData.put("_validUntil", Instant.now().plus(validityDays, ChronoUnit.DAYS).toString());

            log.debug("Returning mdoc claim data with system metadata: {}", claimData.toString(2));
            return claimData;

        } catch (Exception e) {
            log.error("Failed to process mdoc data in MockMDocDataProviderPlugin", e);
            throw new DataProviderExchangeException("ERROR_PROCESSING_MDOC_DATA: " + e);
        }
    }
}