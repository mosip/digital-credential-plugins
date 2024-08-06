package io.mosip.certify.util;


import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class PKCS12Reader {
    public KeyPairAndCertificate extract(String p12FileName, String password, String alias) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, URISyntaxException {
        KeyStore keyStore = loadKeyStore(p12FileName, password.toCharArray());
        Key key = keyStore.getKey(alias, password.toCharArray());
        if (key instanceof PrivateKey privateKey) {
            Certificate cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate x509Certificate) {
                KeyPair keyPair = new KeyPair(x509Certificate.getPublicKey(), privateKey);

                return new KeyPairAndCertificate(keyPair, x509Certificate);
            }
        }
        return null;
    }

    private KeyStore loadKeyStore(String p12FileName, char[] password) {
        try (InputStream keyStoreStream = PKCS12Reader.class.getClassLoader().getResourceAsStream(p12FileName)) {
            if (keyStoreStream == null) {
                throw new IllegalStateException("File not found");
            }
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(keyStoreStream, password);
            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException("Error loading p12 file", e);
        }
    }
}


