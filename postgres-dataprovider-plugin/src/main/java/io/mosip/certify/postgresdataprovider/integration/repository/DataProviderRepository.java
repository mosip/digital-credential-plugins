package io.mosip.certify.postgresdataprovider.integration.repository;


public interface DataProviderRepository {
    Object[] fetchDataFromIdentifier(String id, String queryString);
}