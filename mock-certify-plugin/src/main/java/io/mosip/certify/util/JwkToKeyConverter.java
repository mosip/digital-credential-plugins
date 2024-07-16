//package io.mosip.certify.util;
//
//import com.nimbusds.jose.jwk.JWK;
//import com.nimbusds.jose.jwk.RSAKey;
//import com.nimbusds.jose.jwk.ECKey;
//import com.nimbusds.jose.jwk.OctetSequenceKey;
//import java.security.PrivateKey;
//import java.security.PublicKey;
//
//
//public class JwkToKeyConverter {
//
//    public  PrivateKey convertJwkToPrivateKey(String jwkJson) throws Exception {
//        // Parse the JWK JSON string
//        JWK jwk = JWK.parse(jwkJson);
//
//        // Convert based on the type of the JWK
//        if (jwk instanceof RSAKey) {
//            RSAKey rsaKey = (RSAKey) jwk;
//            if (rsaKey.isPrivate()) {
//                return rsaKey.toPrivateKey();
//            }
//        } else if (jwk instanceof ECKey) {
//            ECKey ecKey = (ECKey) jwk;
//            if (ecKey.isPrivate()) {
//                return ecKey.toPrivateKey();
//            }
//        } else if (jwk instanceof OctetSequenceKey) {
//            // Octet sequence keys are typically used for symmetric encryption (e.g., HMAC keys)
//            throw new IllegalArgumentException("Octet sequence keys do not have a private key component");
//        }
//
//        throw new IllegalArgumentException("Unsupported or invalid JWK type");
//    }
//
//    public  PublicKey convertJwkToPublicKey(String jwkJson) throws Exception {
//        // Parse the JWK JSON string
//        JWK jwk = JWK.parse(jwkJson);
//
//        // Convert based on the type of the JWK
//        if (jwk instanceof RSAKey) {
//            RSAKey rsaKey = (RSAKey) jwk;
//            return rsaKey.toPublicKey();
//        } else if (jwk instanceof ECKey) {
//            ECKey ecKey = (ECKey) jwk;
//            return ecKey.toPublicKey();
//        } else if (jwk instanceof OctetSequenceKey) {
//            // Octet sequence keys are typically used for symmetric encryption (e.g., HMAC keys)
//            throw new IllegalArgumentException("Octet sequence keys do not have a public key component");
//        }
//
//        throw new IllegalArgumentException("Unsupported or invalid JWK type");
//    }
//
//}
