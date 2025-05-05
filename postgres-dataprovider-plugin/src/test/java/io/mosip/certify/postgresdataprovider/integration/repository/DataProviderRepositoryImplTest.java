package io.mosip.certify.postgresdataprovider.integration.repository;

import jakarta.persistence.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DataProviderRepositoryImplTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private Tuple tuple;

    @Mock
    private TupleElement<String> tupleElement;

    @InjectMocks
    private DataProviderRepositoryImpl dataProviderRepository;

    @Before
    public void setup() {
        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(eq("id"), any())).thenReturn(query);
    }

    @Test
    public void fetchQueryResult_Success() {
        // Arrange
        String id = "test-id";
        String queryString = "SELECT * FROM table WHERE id = :id";
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("column1", "value1");
        expectedMap.put("column2", "123");

        List<Tuple> tupleList = Collections.singletonList(tuple);
        when(query.getResultList()).thenReturn(tupleList);

        List<TupleElement<?>> elements = Collections.singletonList(tupleElement);
        when(tuple.getElements()).thenReturn(elements);
        when(tupleElement.getAlias()).thenReturn("column1");
        when(tuple.get(tupleElement)).thenReturn("value1");

        // Act
        Map<String, Object> result = dataProviderRepository.fetchQueryResult(id, queryString);

        // Assert
        assertNotNull(result);
        assertEquals("value1", result.get("column1"));
        verify(entityManager).createNativeQuery(queryString, Tuple.class);
        verify(query).setParameter("id", id);
        verify(query).getResultList();
    }

    @Test
    public void fetchQueryResult_EmptyResultList() {
        // Arrange
        String id = "test-id";
        String queryString = "SELECT * FROM table WHERE id = :id";
        when(query.getResultList()).thenReturn(Collections.emptyList());

        // Act & Assert
        try {
            dataProviderRepository.fetchQueryResult(id, queryString);
            fail("Should throw NoSuchElementException");
        } catch (NoSuchElementException e) {
            // Expected exception
        }
        verify(entityManager).createNativeQuery(queryString, Tuple.class);
        verify(query).setParameter("id", id);
        verify(query).getResultList();
    }

    @Test
    public void convertTuplesToMap_Success() {
        // Arrange
        List<Tuple> tuples = new ArrayList<>();
        tuples.add(tuple);

        List<TupleElement<?>> elements = Arrays.asList(tupleElement, tupleElement);
        when(tuple.getElements()).thenReturn(elements);
        when(tupleElement.getAlias()).thenReturn("column1").thenReturn("column2");
        when(tuple.get(tupleElement)).thenReturn("value1").thenReturn(String.valueOf(123));

        // Act
        List<Map<String, Object>> result = DataProviderRepositoryImpl.convertTuplesToMap(tuples);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, Object> resultMap = result.get(0);
        assertEquals("value1", resultMap.get("column1"));
        assertEquals("123", resultMap.get("column2"));
    }

    @Test
    public void convertTuplesToMap_EmptyList() {
        // Arrange
        List<Tuple> tuples = Collections.emptyList();

        // Act
        List<Map<String, Object>> result = DataProviderRepositoryImpl.convertTuplesToMap(tuples);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}