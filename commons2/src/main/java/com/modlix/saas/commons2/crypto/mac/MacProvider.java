package com.modlix.saas.commons2.crypto.mac;

import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.modlix.saas.commons2.crypto.ImperativeSignatureProvider;
import com.modlix.saas.commons2.crypto.SignatureAlgo;
import com.modlix.saas.commons2.exception.SignatureException;

public class MacProvider extends ImperativeSignatureProvider {

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
