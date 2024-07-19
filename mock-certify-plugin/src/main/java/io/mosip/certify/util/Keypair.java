package io.mosip.certify.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import static java.lang.System.out;

public class Keypair {
    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
    private static final BouncyCastleProvider bouncyCastleProvider;

    static {
        bouncyCastleProvider = BOUNCY_CASTLE_PROVIDER;
    }

    public KeyPair generate() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", bouncyCastleProvider);
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            out.println("Error while keypair generation " + e);
            return null;
        }
    }
}
