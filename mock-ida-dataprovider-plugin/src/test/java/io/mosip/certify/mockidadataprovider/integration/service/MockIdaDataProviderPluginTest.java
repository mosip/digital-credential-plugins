package io.mosip.certify.mockidadataprovider.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import org.json.JSONException;
import org.json.JSONObject;
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
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MockIdaDataProviderPluginTest {
    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache=new NoOpCache("test");

    @Mock
    MockTransactionHelper mockTransactionHelper;

    @Mock
    RestTemplate restTemplate;

    @InjectMocks
    MockIdaDataProviderPlugin mockDataProviderPlugin;

    @Before
    public void setup() throws DataProviderExchangeException {
        ReflectionTestUtils.setField(mockDataProviderPlugin,"getIdentityUrl","http://example.com");
        ReflectionTestUtils.setField(mockDataProviderPlugin,"cacheSecretKeyRefId","cacheSecretKeyRefId");
        ReflectionTestUtils.setField(mockDataProviderPlugin,"aesECBTransformation","AES/ECB/PKCS5Padding");
        ReflectionTestUtils.setField(mockDataProviderPlugin,"storeIndividualId",true);
        ReflectionTestUtils.setField(mockDataProviderPlugin,"isIndividualIDEncrypted",false);

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setTransactionId("test");
        oidcTransaction.setIndividualId("individualId");
        oidcTransaction.setKycToken("kycToken");
        oidcTransaction.setAuthTransactionId("authTransactionId");
        oidcTransaction.setRelyingPartyId("relyingPartyId");
        oidcTransaction.setClaimsLocales(new String[]{"en-US", "en", "en-CA", "fr-FR", "fr-CA"});
        Mockito.when(mockTransactionHelper.getUserInfoTransaction("ACCESS_TOKEN_HASH")).thenReturn(oidcTransaction);

        Map<String, Object> identityJson = new HashMap<>();
        identityJson.put("fullName", "fullName");
        identityJson.put("gender", "gender");
        identityJson.put("dateOfBirth", "dateOfBirth");
        identityJson.put("email", "email");
        identityJson.put("phone", "phone");
        identityJson.put("streetAddress", "streetAddress");
        identityJson.put("locality", "locality");
        identityJson.put("region", "region");
        identityJson.put("postalCode", "postalCode");
        identityJson.put("encodedPhoto", "encodedPhoto");
        Map<String, Object> response = new HashMap<>();
        response.put("response", identityJson);
        Mockito.when(restTemplate.getForObject(Mockito.anyString(), Mockito.any())).thenReturn(response);
    }

    @Test
    public void getJSONDataWithValidDetails_thenPass() throws DataProviderExchangeException, JSONException {
        JSONObject jsonData = mockDataProviderPlugin.fetchData(Map.of("accessTokenHash","ACCESS_TOKEN_HASH","client_id","CLIENT_ID"));
        Assert.assertNotNull(jsonData);
        Assert.assertNotNull(jsonData.get("fullName"));
        Assert.assertEquals("fullName" ,jsonData.get("fullName"));
        Assert.assertNotNull(jsonData.get("UIN"));
        Assert.assertEquals("individualId", jsonData.get("UIN"));
        Assert.assertNotNull(jsonData.get("id"));
        Assert.assertEquals("http://example.com/individualId", jsonData.get("id"));
    }

    @Test
    public void getJSONDataWithInValidDetails_thenFail() {
        try {
            mockDataProviderPlugin.fetchData(Map.of("accessTokenHash","test","client_id","CLIENT_ID"));
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_IDENTITY_DATA", e.getMessage());
        }
    }
}