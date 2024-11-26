package io.mosip.certify.postgresdataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;


@Repository(value = "dataProviderRepository")
public class DataProviderRepositoryImpl implements DataProviderRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Object[] fetchDataFromIdentifier(String id, String queryString) {
        Query query = entityManager.createNativeQuery(queryString);
        query.setParameter("id", id);
        return (Object[]) query.getSingleResult();
    }
}