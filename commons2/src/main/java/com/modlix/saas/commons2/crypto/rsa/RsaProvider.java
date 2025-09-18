package com.modlix.saas.commons2.crypto.rsa;

import java.security.Key;

import com.modlix.saas.commons2.crypto.ImperativeSignatureProvider;
import com.modlix.saas.commons2.crypto.SignatureAlgo;

public class RsaProvider extends ImperativeSignatureProvider {

	protected RsaProvider(SignatureAlgo alg, Key key) {
		super(alg, key);
	}
}
