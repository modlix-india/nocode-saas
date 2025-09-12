package com.modlix.saas.commons2.crypto.rsa;

import java.security.Key;

import com.modlix.saas.commons2.crypto.ImperativeSigner;
import com.modlix.saas.commons2.crypto.SignatureAlgo;
import com.modlix.saas.commons2.exception.SignatureException;

public class RsaSigner extends RsaProvider implements ImperativeSigner {

	public RsaSigner(SignatureAlgo alg, Key key) {
		super(alg, key);
	}

	@Override
	public byte[] sign(byte[] data) throws SignatureException {
		return null;
	}

	@Override
	public boolean isValid(byte[] data, byte[] signature) {
		return false;
	}
}
