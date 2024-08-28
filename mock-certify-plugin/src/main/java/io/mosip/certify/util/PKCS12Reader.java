package io.mosip.certify.util;


import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Slf4j
public class PKCS12Reader {
    public KeyPairAndCertificate extract(String keyCert) {
        String[] splitKeyCert = keyCert.split("\\|\\|");
        try {
            X509Certificate certificate = convertStringToX509Certificate((splitKeyCert[1]));
            return (new KeyPairAndCertificate(getKeyPair(splitKeyCert[0], certificate), certificate));
        } catch (Exception e) {
            log.error("Failed to extract key certificate", e);
        }

        return null;
    }

    private X509Certificate convertStringToX509Certificate(String certString) throws CertificateException {
        byte[] certBytes = Base64.getDecoder().decode(certString);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private KeyPair getKeyPair(String base64PrivateKey, X509Certificate certificate) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privateKeyBytes = Base64.getDecoder().decode(base64PrivateKey);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        PublicKey publicKey = certificate.getPublicKey();

        return new KeyPair(publicKey, privateKey);
    }
}


