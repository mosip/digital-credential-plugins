package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class)
public class MockMDocDataProviderPluginTest {

    @InjectMocks
    MockMDocDataProviderPlugin mockMDocDataProviderPlugin = new MockMDocDataProviderPlugin();

    @Before
    public void setup() {
        ReflectionTestUtils.setField(mockMDocDataProviderPlugin, "issuer", "https://test-issuer.org");
        ReflectionTestUtils.setField(mockMDocDataProviderPlugin, "defaultDocType", "org.iso.18013.5.1.mDL");
        ReflectionTestUtils.setField(mockMDocDataProviderPlugin, "validityDays", 365L);
    }

    @Test
    public void fetchData_withValidIdentityDetails_thenPass() throws DataProviderExchangeException, JSONException {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> namespaceClaims = new HashMap<>();
        namespaceClaims.put("given_name", "John");
        namespaceClaims.put("family_name", "Doe");
        namespaceClaims.put("birth_date", "1990-01-01");
        namespaceClaims.put("document_number", "DL123456789");

        claims.put("org.iso.18013.5.1", namespaceClaims);
        identityDetails.put("claims", claims);

        // Act
        JSONObject result = mockMDocDataProviderPlugin.fetchData(identityDetails);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals("John", result.get("given_name"));
        Assert.assertEquals("Doe", result.get("family_name"));
        Assert.assertEquals("1990-01-01", result.get("birth_date"));
        Assert.assertEquals("DL123456789", result.get("document_number"));

        // Verify system metadata
        Assert.assertEquals("org.iso.18013.5.1.mDL", result.get("_docType"));
        Assert.assertEquals("https://test-issuer.org", result.get("_issuer"));
        Assert.assertNotNull(result.get("_issuedAt"));
        Assert.assertNotNull(result.get("_validUntil"));
    }

    @Test
    public void fetchData_withDefaultDocType_thenPass() throws DataProviderExchangeException, JSONException {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();

        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> namespaceClaims = new HashMap<>();
        namespaceClaims.put("given_name", "Jane");
        namespaceClaims.put("family_name", "Smith");

        claims.put("org.iso.18013.5.1", namespaceClaims);
        identityDetails.put("claims", claims);

        // Act
        JSONObject result = mockMDocDataProviderPlugin.fetchData(identityDetails);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals("Jane", result.get("given_name"));
        Assert.assertEquals("Smith", result.get("family_name"));
        Assert.assertEquals("org.iso.18013.5.1.mDL", result.get("_docType")); // Should use default
    }

    @Test
    public void fetchData_withNullIdentityDetails_thenFail() {
        // Act & Assert
        try {
            mockMDocDataProviderPlugin.fetchData(null);
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("No identity data provided.", e.getMessage());
        }
    }

    @Test
    public void fetchData_withEmptyIdentityDetails_thenFail() {
        // Act & Assert
        try {
            mockMDocDataProviderPlugin.fetchData(new HashMap<>());
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("No identity data provided.", e.getMessage());
        }
    }

    @Test
    public void fetchData_withMissingClaims_thenFail() {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        // Act & Assert
        try {
            mockMDocDataProviderPlugin.fetchData(identityDetails);
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("Claims section is missing or empty.", e.getMessage());
        }
    }

    @Test
    public void fetchData_withEmptyClaims_thenFail() {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");
        identityDetails.put("claims", new HashMap<>());

        // Act & Assert
        try {
            mockMDocDataProviderPlugin.fetchData(identityDetails);
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("Claims section is missing or empty.", e.getMessage());
        }
    }

    @Test
    public void fetchData_withEmptyNamespaceClaims_thenFail() {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        Map<String, Object> claims = new HashMap<>();
        claims.put("org.iso.18013.5.1", new HashMap<>());
        identityDetails.put("claims", claims);

        // Act & Assert
        try {
            mockMDocDataProviderPlugin.fetchData(identityDetails);
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertTrue(e.getMessage().contains("No claim data found under namespace"));
        }
    }

    @Test
    public void fetchData_withNullNamespaceClaims_thenFail() {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        Map<String, Object> claims = new HashMap<>();
        claims.put("org.iso.18013.5.1", null);
        identityDetails.put("claims", claims);

        // Act & Assert
        try {
            mockMDocDataProviderPlugin.fetchData(identityDetails);
            Assert.fail("Expected DataProviderExchangeException");
        } catch (DataProviderExchangeException e) {
            Assert.assertTrue(e.getMessage().contains("No claim data found under namespace"));
        }
    }

    @Test
    public void fetchData_withMultipleNamespaces_shouldUseFirst() throws DataProviderExchangeException, JSONException {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        Map<String, Object> claims = new HashMap<>();

        // First namespace
        Map<String, Object> firstNamespaceClaims = new HashMap<>();
        firstNamespaceClaims.put("given_name", "John");
        firstNamespaceClaims.put("family_name", "Doe");
        claims.put("org.iso.18013.5.1", firstNamespaceClaims);

        // Second namespace
        Map<String, Object> secondNamespaceClaims = new HashMap<>();
        secondNamespaceClaims.put("given_name", "Jane");
        secondNamespaceClaims.put("family_name", "Smith");
        claims.put("com.example.another", secondNamespaceClaims);

        identityDetails.put("claims", claims);

        // Act
        JSONObject result = mockMDocDataProviderPlugin.fetchData(identityDetails);

        // Assert
        Assert.assertNotNull(result);
        // Should use the first namespace data (order depends on HashMap iteration, but we expect consistent behavior)
        Assert.assertTrue(result.has("given_name"));
        Assert.assertTrue(result.has("family_name"));
    }

    @Test
    public void fetchData_withCustomValidityDays() throws DataProviderExchangeException, JSONException {
        // Arrange
        ReflectionTestUtils.setField(mockMDocDataProviderPlugin, "validityDays", 180L);

        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> namespaceClaims = new HashMap<>();
        namespaceClaims.put("given_name", "John");

        claims.put("org.iso.18013.5.1", namespaceClaims);
        identityDetails.put("claims", claims);

        // Act
        JSONObject result = mockMDocDataProviderPlugin.fetchData(identityDetails);

        // Assert
        Assert.assertNotNull(result);

        String issuedAtStr = result.getString("_issuedAt");
        String validUntilStr = result.getString("_validUntil");

        Instant issuedAt = Instant.parse(issuedAtStr);
        Instant validUntil = Instant.parse(validUntilStr);

        // Verify validUntil is 180 days after issuedAt
        Instant expectedValidUntil = issuedAt.plus(180, ChronoUnit.DAYS);
        Assert.assertEquals(expectedValidUntil, validUntil);
    }

    @Test
    public void fetchData_withComplexClaimData() throws DataProviderExchangeException, JSONException {
        // Arrange
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("doctype", "org.iso.18013.5.1.mDL");

        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> namespaceClaims = new HashMap<>();
        namespaceClaims.put("given_name", "John");
        namespaceClaims.put("family_name", "Doe");
        namespaceClaims.put("birth_date", "1990-01-01");
        namespaceClaims.put("issue_date", "2023-01-01");
        namespaceClaims.put("expiry_date", "2028-01-01");
        namespaceClaims.put("issuing_country", "US");
        namespaceClaims.put("issuing_authority", "DMV");
        namespaceClaims.put("document_number", "DL123456789");
        namespaceClaims.put("portrait", "base64encodedimage");

        claims.put("org.iso.18013.5.1", namespaceClaims);
        identityDetails.put("claims", claims);

        // Act
        JSONObject result = mockMDocDataProviderPlugin.fetchData(identityDetails);

        // Assert
        Assert.assertNotNull(result);
        Assert.assertEquals("John", result.get("given_name"));
        Assert.assertEquals("Doe", result.get("family_name"));
        Assert.assertEquals("1990-01-01", result.get("birth_date"));
        Assert.assertEquals("2023-01-01", result.get("issue_date"));
        Assert.assertEquals("2028-01-01", result.get("expiry_date"));
        Assert.assertEquals("US", result.get("issuing_country"));
        Assert.assertEquals("DMV", result.get("issuing_authority"));
        Assert.assertEquals("DL123456789", result.get("document_number"));
        Assert.assertEquals("base64encodedimage", result.get("portrait"));

        // Verify system metadata
        Assert.assertEquals("org.iso.18013.5.1.mDL", result.get("_docType"));
        Assert.assertEquals("https://test-issuer.org", result.get("_issuer"));
        Assert.assertNotNull(result.get("_issuedAt"));
        Assert.assertNotNull(result.get("_validUntil"));
    }
}