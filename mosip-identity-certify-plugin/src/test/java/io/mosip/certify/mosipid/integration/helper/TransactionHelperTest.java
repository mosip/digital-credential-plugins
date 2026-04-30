/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.certify.mosipid.integration.helper;

import io.mosip.esignet.core.dto.OIDCTransaction;
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
public class TransactionHelperTest {

    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache=new NoOpCache("test");

    @InjectMocks
    TransactionHelper transactionHelper;

    @Test
    public void getOAuthTransactionWithValidDetails_thenPass() throws Exception {
        ReflectionTestUtils.setField(transactionHelper, "userinfoCache", "test");
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setTransactionId("test");
        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);
        Mockito.when(cache.get("test",OIDCTransaction.class)).thenReturn(oidcTransaction);
        transactionHelper.getOAuthTransaction("test");
    }

    @Test
    public void getOAuthTransactionWithInValidDetails_thenFail() {
        try{
            transactionHelper.getOAuthTransaction("test");
        }catch (Exception e){
            assert(e.getMessage().equals("cache_missing"));
        }
    }

}
