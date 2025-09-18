package com.modlix.saas.commons2.crypto.mac;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.modlix.saas.commons2.crypto.ImperativeSigner;
import com.modlix.saas.commons2.crypto.SignatureAlgo;
import com.modlix.saas.commons2.exception.SignatureException;

public class MacSigner extends MacProvider implements ImperativeSigner {

	public MacSigner(SignatureAlgo alg, byte[] key) {
		this(alg, new SecretKeySpec(key, alg.getJcaName()), false);
	}

	public MacSigner(SignatureAlgo alg, byte[] key, boolean verify) {
		this(alg, new SecretKeySpec(key, alg.getJcaName()), verify);
	}

	public MacSigner(SignatureAlgo alg, Key key, boolean verify) {
		super(alg, key);
		if (verify)
			alg.isValidVerificationKey(key);
		else
			alg.isValidSigningKey(key);
	}

	@Override
	public byte[] sign(byte[] data) throws SignatureException {
		try {
			Mac mac = getMacInstance();
			return mac.doFinal(data);
		} catch (Exception e) {
			if (e instanceof NoSuchAlgorithmException || e instanceof InvalidKeyException)
				throw new SignatureException("Error during signing operation");
			throw e;
		}
	}

	@Override
	public boolean isValid(byte[] data, byte[] signature) {
		try {
			byte[] macSignature = this.sign(data);
			return MessageDigest.isEqual(macSignature, signature);
		} catch (Exception e) {
			return false;
		}
	}

	protected Mac getMacInstance() throws SignatureException {
		try {
			return doGetMacInstance();
		} catch (NoSuchAlgorithmException e) {
			throw new SignatureException(
					"Unable to obtain JCA MAC algorithm '" + alg.getJcaName() + "': " + e.getMessage());
		} catch (InvalidKeyException e) {
			throw new SignatureException(
					"The specified signing key is not a valid " + alg.name() + " key: " + e.getMessage());
		}
	}

	protected Mac doGetMacInstance() throws NoSuchAlgorithmException, InvalidKeyException {
		Mac mac = Mac.getInstance(alg.getJcaName());
		mac.init(key);
		return mac;
	}
}
