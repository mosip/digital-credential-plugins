package io.mosip.certify.util;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;


public class
JwkToKeyConverter {

    public  PublicKey convertToPublicKey(String encodedData) throws Exception {
        String jwkJsonString = new String(Base64.getUrlDecoder().decode(encodedData), StandardCharsets.UTF_8);
        JWK jwk = JWK.parse(jwkJsonString);

        if (jwk instanceof RSAKey) {
            return ((RSAKey) jwk).toPublicKey();
        } else if (jwk instanceof ECKey) {
            return ((ECKey) jwk).toPublicKey();
        }

        throw new IllegalArgumentException("Unsupported key type");
    }

}
