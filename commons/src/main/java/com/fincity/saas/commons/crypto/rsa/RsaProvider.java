package com.fincity.saas.commons.crypto.rsa;

import com.fincity.saas.commons.crypto.ReactiveSignatureProvider;
import com.fincity.saas.commons.crypto.SignatureAlgo;
import java.security.Key;

public class RsaProvider extends ReactiveSignatureProvider {

    protected RsaProvider(SignatureAlgo alg, Key key) {
        super(alg, key);
    }
}
