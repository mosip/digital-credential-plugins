/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.certify.mosipid.integration.service;


import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;


@RunWith(MockitoJUnitRunner.class)
public class HelperServiceTest {

    @InjectMocks
    private HelperService helperService;

    @Mock
    private SignatureService signatureService;

    @Test
    public void getRequestSignature_validation() {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);
        Assert.assertEquals("test-jwt", helperService.getRequestSignature("test-request-value"));
    }

    @Test
    public void b64Encode_shouldReturnBase64UrlSafeEncoding() {
        String input = "test-value";
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
        String actual = HelperService.b64Encode(input);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void getUTCDateTime_shouldReturnValidUTCFormattedDateTime() {
        String utcNow = HelperService.getUTCDateTime();
        // Check format matches expected pattern
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(HelperService.UTC_DATETIME_PATTERN);
        ZonedDateTime parsedDate = ZonedDateTime.parse(utcNow, formatter.withZone(ZoneOffset.UTC));
        Assert.assertNotNull(parsedDate);
    }
}
