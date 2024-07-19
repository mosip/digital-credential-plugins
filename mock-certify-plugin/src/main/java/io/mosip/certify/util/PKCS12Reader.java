package io.mosip.certify.util;


import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public class PKCS12Reader {

    public static IssuerKeyPairAndCertificate read() throws Exception {
        String p12FilePath = "/Users/kiruthikajeyashankar/MyWorkspace/Projects/MOSIP/tw-mosip/mdoc-vc-issuer/src/main/kotlin/util/client-identity-1.p12";
        String alias = "myalias";
        char[] password = "password".toCharArray();

        // Load the P12 file
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(p12FilePath)) {
            keyStore.load(fis, password);
        }

        // Get the private key
        Key key = keyStore.getKey(alias, password);
        if (key instanceof PrivateKey) {
            PrivateKey privateKey = (PrivateKey) key;

            // Get the certificate
            Certificate cert = keyStore.getCertificate(alias);
//            System.out.println("cert " + cert);

            // Get the public key from the certificate
            if (cert instanceof X509Certificate) {
                X509Certificate x509Certificate = (X509Certificate) cert;
                KeyPair keyPair = new KeyPair(x509Certificate.getPublicKey(), privateKey);

                // Print the key pair and certificate
                System.out.println("Private Key: " + keyPair.getPrivate());
                System.out.println("Public Key: " + keyPair.getPublic());
                System.out.println("Certificate: " + x509Certificate);

                return new IssuerKeyPairAndCertificate(keyPair, x509Certificate);
            }
        }
        return null;
    }
}

