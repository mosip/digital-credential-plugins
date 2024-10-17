package io.mosip.certify.mockidadataprovider.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCache;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class MockTransactionHelperTest {
    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache=new NoOpCache("test");

    @InjectMocks
    MockTransactionHelper mockTransactionHelper;

    @Before
    public void setup() {
        ReflectionTestUtils.setField(mockTransactionHelper, "userinfoCache", "test");
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setTransactionId("test");
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");
        oidcTransaction.setClaimsLocales(new String[]{"en-US", "en", "en-CA", "fr-FR", "fr-CA"});

        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);
        Mockito.when(cache.get("test", OIDCTransaction.class)).thenReturn(oidcTransaction);
    }

    @Test
    public void getOIDCTransactionWithValidDetails_thenPass() throws DataProviderExchangeException {
        OIDCTransaction transaction = mockTransactionHelper.getUserInfoTransaction("test");
        Assert.assertNotNull(transaction);
        Assert.assertEquals("test", transaction.getTransactionId());
        Assert.assertEquals("individualId", transaction.getIndividualId());
    }

    @Test
    public void getOIDCTransactionWithInValidUserinfo_thenFail() throws DataProviderExchangeException {
        OIDCTransaction transaction = mockTransactionHelper.getUserInfoTransaction("ACCESS_TOKEN_HASH");
        Assert.assertNull(transaction);
    }
}
