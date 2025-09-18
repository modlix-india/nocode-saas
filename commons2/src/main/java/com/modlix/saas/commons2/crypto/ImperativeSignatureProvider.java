package com.modlix.saas.commons2.crypto;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.modlix.saas.commons2.exception.SignatureException;

public abstract class ImperativeSignatureProvider {

    public static final SecureRandom DEFAULT_SECURE_RANDOM;

    static {
        DEFAULT_SECURE_RANDOM = new SecureRandom();
        DEFAULT_SECURE_RANDOM.nextBytes(new byte[64]);
    }

    protected final SignatureAlgo alg;
    protected final Key key;

    protected ImperativeSignatureProvider(SignatureAlgo alg, Key key) {
        this.alg = alg;
        this.key = key;
    }

    protected Signature createSignatureInstance() {
        try {
            return getSignatureInstance();
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureException(StringFormatter.format("Unavailable $ : Signature algorithm : '$'.",
                    alg.getFamilyName(), alg.getJcaName()));
        }
    }

    protected Signature getSignatureInstance() throws NoSuchAlgorithmException {
        return Signature.getInstance(alg.getJcaName());
    }

}
