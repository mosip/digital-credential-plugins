package io.mosip.certify.postgresdataprovider.integration.repository;


import java.util.Map;

public interface DataProviderRepository {
    Map<String, Object> fetchQueryResult(String id, String queryString);
}