package io.mosip.certify.mockpostgresdataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

@Repository(value = "mockDataRepository")
public class MockDataRepositoryImpl implements MockDataRepository {
    @PersistenceContext
    private EntityManager entityManager;
    @Override
    public Object[] getIdentityDataFromIndividualId(String id) {
        String queryString = "select statement_id, statement_json from statement_data where statement_id=:id";
        Query query = entityManager.createNativeQuery(queryString);
        query.setParameter("id", id);
        return (Object[]) query.getSingleResult();
    }
}