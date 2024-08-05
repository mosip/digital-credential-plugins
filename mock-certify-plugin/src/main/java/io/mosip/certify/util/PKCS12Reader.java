package io.mosip.certify.util;


import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class PKCS12Reader {

    public static IssuerKeyPairAndCertificate read() throws Exception {
        String p12FilePath = "/Users/kiruthikajeyashankar/MyWorkspace/Projects/MOSIP/tw-mosip/mdoc-vc-issuer/src/main/resources/mock-issuer-details.p12";
        String alias = "mock-issuer";
        char[] password = "mosip123".toCharArray();
        System.out.println("Issuer");
        Object[] issuerDetails = extract(p12FilePath, password, alias);
        System.out.println("CA");
        Object[] caDetails = extract(p12FilePath, password, "mock-issuer-ca");
        assert caDetails != null;
        return new IssuerKeyPairAndCertificate((KeyPair) issuerDetails[0], (X509Certificate) issuerDetails[1], (X509Certificate) caDetails[1]);
    }

    private static @Nullable Object[] extract(String p12FilePath, char[] password, String alias) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(p12FilePath)) {
            keyStore.load(fis, password);
        }
// Iterate over the aliases in the keystore
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            System.out.println("aliases " + aliases.nextElement());
        }
        Key key = keyStore.getKey(alias, password);
        if (key instanceof PrivateKey) {
            PrivateKey privateKey = (PrivateKey) key;

            Certificate cert = keyStore.getCertificate(alias);
//            System.out.println("cert " + cert);

            if (cert instanceof X509Certificate) {
                X509Certificate x509Certificate = (X509Certificate) cert;
                KeyPair keyPair = new KeyPair(x509Certificate.getPublicKey(), privateKey);

                // Print the key pair and certificate
                System.out.println("Private Key: " + keyPair.getPrivate());
                System.out.println("Public Key: " + keyPair.getPublic());
                System.out.println("Certificate: " + x509Certificate);

                return new Object[]{keyPair, x509Certificate};
            }
        }
        return null;
    }
}


