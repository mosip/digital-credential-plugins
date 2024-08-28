package io.mosip.certify.util;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record IssuerKeyPairAndCertificate(KeyPair issuerKeypair, X509Certificate issuerCertificate,
                                          X509Certificate caCertificate) {
}
