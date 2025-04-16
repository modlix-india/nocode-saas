package com.fincity.saas.commons.crypto.rsa;

import java.security.Key;

import com.fincity.saas.commons.crypto.ReactiveSignatureProvider;
import com.fincity.saas.commons.crypto.SignatureAlgo;

public class RsaProvider extends ReactiveSignatureProvider {

	protected RsaProvider(SignatureAlgo alg, Key key) {
		super(alg, key);
	}
}
