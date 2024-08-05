package io.mosip.certify.util;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public class IssuerKeyPairAndCertificate {
    private final KeyPair issuerKeypair;
    private final X509Certificate issuerCertificate;
    private final X509Certificate caCertificate;

    public IssuerKeyPairAndCertificate(KeyPair issuerKeypair, X509Certificate issuerCertificate, X509Certificate caCertificate) {
        this.issuerKeypair = issuerKeypair;
        this.caCertificate = caCertificate;
        this.issuerCertificate = issuerCertificate;
    }

    public KeyPair getIssuerKeypair() {
        return issuerKeypair;
    }

    public X509Certificate getIssuerCertificate() {
        return issuerCertificate;
    }

    public X509Certificate getCaCertificate() {
        return caCertificate;
    }
}
