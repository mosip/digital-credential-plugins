package io.mosip.certify.mockpostgresdataprovider.integration.repository;


public interface MockDataRepository {
    Object[] getIdentityDataFromIndividualId(String id);
}
