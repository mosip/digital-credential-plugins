package io.mosip.certify.util;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record KeyPairAndCertificate(KeyPair keyPair, X509Certificate certificate) {
}
