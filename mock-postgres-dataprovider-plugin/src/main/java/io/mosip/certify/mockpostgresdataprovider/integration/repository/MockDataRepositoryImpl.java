package io.mosip.certify.mockpostgresdataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository(value = "mockDataRepository")
public class MockDataRepositoryImpl implements MockDataRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${mosip.certify.postgres.data-provider.table-name}")
    private String tableName;

    @Value("${mosip.certify.postgres.data-provider.table-identifier}")
    private String tableIdentifier;

    @Override
    public Object[] getIdentityDataFromIndividualId(String id) {
        String queryString = "select * from " + tableName  + " where "  + tableIdentifier + "=:id";
        Query query = entityManager.createNativeQuery(queryString);
        query.setParameter("id", id);
        return (Object[]) query.getSingleResult();
    }
}