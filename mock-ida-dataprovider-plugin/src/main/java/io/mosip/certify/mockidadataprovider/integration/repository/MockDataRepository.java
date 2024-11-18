package io.mosip.certify.mockidadataprovider.integration.repository;

import java.util.List;

public interface MockDataRepository {
    Object[] getIdentityDataFromIndividualId(String id);
}
