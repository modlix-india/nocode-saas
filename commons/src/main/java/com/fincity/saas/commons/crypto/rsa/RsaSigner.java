package com.fincity.saas.commons.crypto.rsa;

import com.fincity.saas.commons.crypto.ReactiveSigner;
import com.fincity.saas.commons.crypto.SignatureAlgo;
import com.fincity.saas.commons.exeception.SignatureException;
import java.security.Key;
import reactor.core.publisher.Mono;

public class RsaSigner extends RsaProvider implements ReactiveSigner {

    public RsaSigner(SignatureAlgo alg, Key key) {
        super(alg, key);
    }

    @Override
    public Mono<byte[]> sign(byte[] data) throws SignatureException {
        return null;
    }

    @Override
    public Mono<Boolean> isValid(byte[] data, byte[] signature) {
        return null;
    }
}
