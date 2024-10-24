package io.mosip.certify.mockidadataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository(value = "mockDataRepository")
public class MockDataRepositoryImpl implements MockDataRepository {
    @PersistenceContext
    private EntityManager entityManager;
    @Override
    public Object[] getIdentityDataFromIndividualId(String id) {
        String queryString = "select individual_id, identity_json from mock_identity where individual_id=:id";
        Query query = entityManager.createNativeQuery(queryString);
        query.setParameter("id", id);
        return (Object[]) query.getSingleResult();
    }
}
