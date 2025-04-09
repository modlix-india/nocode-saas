package com.fincity.saas.commons.crypto.mac;

import com.fincity.saas.commons.crypto.ReactiveSignatureProvider;
import com.fincity.saas.commons.crypto.SignatureAlgo;
import com.fincity.saas.commons.exeception.SignatureException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class MacProvider extends ReactiveSignatureProvider {

    protected MacProvider(SignatureAlgo alg, Key key) {
        super(alg, key);
        if (!alg.isHmac())
            throw new SignatureException("SignatureAlgorithm argument must represent an HMAC algorithm.");
    }

    public static SecretKey generateKey() {
        return generateKey(SignatureAlgo.HS512);
    }

    public static SecretKey generateKey(SignatureAlgo alg) {

        if (!alg.isHmac())
            throw new SignatureException("SignatureAlgorithm argument must represent an HMAC algorithm.");

        KeyGenerator gen;

        try {
            gen = KeyGenerator.getInstance(alg.getJcaName());
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureException("The " + alg.getJcaName() + " algorithm is not available.");
        }
        return gen.generateKey();
    }
}
