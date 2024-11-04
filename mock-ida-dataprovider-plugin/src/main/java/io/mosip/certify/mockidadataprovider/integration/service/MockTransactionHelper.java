package io.mosip.certify.mockidadataprovider.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class MockTransactionHelper {
    @Value("${mosip.certify.mock.vci-user-info-cache:userinfo}")
    private String userinfoCache;

    @Autowired
    private CacheManager cacheManager;

    public OIDCTransaction getUserInfoTransaction(String accessTokenHash) throws DataProviderExchangeException {
        if(cacheManager.getCache(userinfoCache) != null) {
            return cacheManager.getCache(userinfoCache).get(accessTokenHash, OIDCTransaction.class);
        }

        throw new DataProviderExchangeException("CACHE_MISSING");
    }
}