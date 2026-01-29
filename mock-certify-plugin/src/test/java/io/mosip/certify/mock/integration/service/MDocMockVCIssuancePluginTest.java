package io.mosip.certify.mock.integration.service;

import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.constants.VCFormats;
import io.mosip.certify.mock.integration.mocks.MdocGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class MDocMockVCIssuancePluginTest {

    @InjectMocks
    private MDocMockVCIssuancePlugin plugin;

    @Mock
    private MdocGenerator mdocGenerator;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(plugin, "issuerKeyAndCertificate", "empty");
    }

    @Test
    public void testGetVerifiableCredential_Success() throws Exception {
        VCRequestDto dto = mock(VCRequestDto.class);
        when(dto.getFormat()).thenReturn(VCFormats.MSO_MDOC);

        // identityDetails must contain "sub" because implementation casts it to String
        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("sub", "12345");
        identityDetails.put("accessTokenHash", "tokenHash");

        when(mdocGenerator.generate(anyMap(), anyString(), anyString()))
                .thenReturn("mockedMdoc");

        VCResult<String> result = plugin.getVerifiableCredential(dto, "holderId", identityDetails);

        assertNotNull(result);
        // Ensure we compare like types; adjust if VCFormats.MSO_MDOC is not a String
        assertEquals(VCFormats.MSO_MDOC, result.getFormat());
        assertEquals("mockedMdoc", result.getCredential());
    }


    @Test(expected = VCIExchangeException.class)
    public void testGetVerifiableCredential_NotImplemented() throws Exception {
        VCRequestDto dto = mock(VCRequestDto.class);

        // Use a format that MDocMockVCIssuancePlugin does not implement
        when(dto.getFormat()).thenReturn(VCFormats.LDP_VC); // or any other unsupported format

        Map<String, Object> identityDetails = new HashMap<>();
        identityDetails.put("accessTokenHash", "tokenHash");
        identityDetails.put("sub", "12345");

        plugin.getVerifiableCredential(dto, "holderId", identityDetails);
    }


    @Test(expected = VCIExchangeException.class)
    public void testGetVerifiableCredentialWithLinkedDataProof_NotImplemented() throws Exception {
        VCRequestDto dto = mock(VCRequestDto.class);
        plugin.getVerifiableCredentialWithLinkedDataProof(dto, "holderId", new HashMap<>());
    }

    @Test
    public void testMockDataForMsoMdoc() {
        String docNum = "12345";
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ReflectionTestUtils.invokeMethod(plugin, "mockDataForMsoMdoc", docNum);
        assertNotNull(data);
        assertEquals("Agatha", data.get("family_name"));
        assertEquals("Joseph", data.get("given_name"));
        assertEquals("1994-11-06", data.get("birth_date"));
        assertEquals("IN", data.get("issuing_country"));
        assertEquals(docNum, data.get("document_number"));
        assertTrue(data.get("driving_privileges") instanceof Map);
        assertEquals("A", ((Map<?, ?>) data.get("driving_privileges")).get("vehicle_category_code"));
    }
}