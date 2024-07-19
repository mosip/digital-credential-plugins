package io.mosip.certify.util;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public class IssuerKeyPairAndCertificate {
    private final KeyPair keyPair;
    private final X509Certificate x509Certificate;

    public IssuerKeyPairAndCertificate(KeyPair keyPair, X509Certificate x509Certificate) {
        this.keyPair = keyPair;
        this.x509Certificate = x509Certificate;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }
}
