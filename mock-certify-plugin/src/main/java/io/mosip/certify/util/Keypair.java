package io.mosip.certify.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import static java.lang.System.out;

public class Keypair {
    public KeyPair generate() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            out.println("Error while keypair generation " + e);
            return null;
        }
    }
}
