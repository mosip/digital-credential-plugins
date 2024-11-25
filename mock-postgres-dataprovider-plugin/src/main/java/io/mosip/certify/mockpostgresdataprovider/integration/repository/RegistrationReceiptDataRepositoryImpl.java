package io.mosip.certify.mockpostgresdataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;


@Repository
@ConditionalOnProperty(value = "mosip.certify.postgres.credential.name", havingValue = "RegistrationReceiptData")
public class RegistrationReceiptDataRepositoryImpl implements MockDataRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Object[] getIdentityDataFromIndividualId(String id) {
        String queryString = "select * from registration_receipt_data where registration_id=:id";
        Query query = entityManager.createNativeQuery(queryString);
        query.setParameter("id", id);
        return (Object[]) query.getSingleResult();
    }
}