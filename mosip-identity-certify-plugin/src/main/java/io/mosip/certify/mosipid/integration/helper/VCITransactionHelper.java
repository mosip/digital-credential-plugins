/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.certify.mosipid.integration.helper;

import io.mosip.esignet.core.dto.OIDCTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "mosip.certify.integration.vci-plugin", havingValue = "IdaVCIssuancePluginImpl")
public class VCITransactionHelper {

	@Autowired
	CacheManager cacheManager;

	@Value("${mosip.certify.ida.vci-user-info-cache}")
	private String userinfoCache;

	@SuppressWarnings("unchecked")
	public OIDCTransaction getOAuthTransaction(String accessTokenHash) throws Exception {
		if (cacheManager.getCache(userinfoCache) != null) {
			return cacheManager.getCache(userinfoCache).get(accessTokenHash, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
		}
		throw new Exception("cache_missing");
	}

}
