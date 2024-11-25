package io.mosip.certify.mockpostgresdataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(value = "mosip.certify.postgres.credential.name", havingValue = "StatementData")
public class StatementDataRepositoryImpl implements MockDataRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Object[] getIdentityDataFromIndividualId(String id) {
        String queryString = "select * from statement_data where statement_id=:id";
        Query query = entityManager.createNativeQuery(queryString);
        query.setParameter("id", id);
        return (Object[]) query.getSingleResult();
    }
}
