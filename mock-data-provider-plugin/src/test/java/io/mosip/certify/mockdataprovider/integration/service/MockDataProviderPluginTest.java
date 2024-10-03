package io.mosip.certify.mockdataprovider.integration.service;

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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MockDataProviderPluginTest {
    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache=new NoOpCache("test");

    @InjectMocks
    MockDataProviderPlugin mockDataProviderPlugin = new MockDataProviderPlugin();



    @Before
    public void setUp() {
        ReflectionTestUtils.setField(mockDataProviderPlugin,"getIdentityUrl","http://example.com");
        ReflectionTestUtils.setField(mockDataProviderPlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");
        ReflectionTestUtils.setField(mockDataProviderPlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(mockDataProviderPlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(mockDataProviderPlugin,"secureIndividualId",false);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");
        oidcTransaction.setClaimsLocales(new String[]{"en-US", "en", "en-CA", "fr-FR", "fr-CA"});

        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);
    }

    @Test
    public void getJSONData() throws DataProviderExchangeException {
        Map<String, Object> jsonData = mockDataProviderPlugin.fetchData(Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNull(jsonData);
//        Assert.assertNotNull(jsonData.get("type"));
//        List<String> expectedType = Arrays.asList("VerifiableCredential", "MockVerifiableCredential");
//        Assert.assertEquals(expectedType, jsonData.get("type"));
    }
}