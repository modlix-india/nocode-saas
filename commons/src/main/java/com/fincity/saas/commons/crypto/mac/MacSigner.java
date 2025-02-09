package com.fincity.saas.commons.crypto.mac;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fincity.saas.commons.crypto.ReactiveSigner;
import com.fincity.saas.commons.crypto.SignatureAlgo;
import com.fincity.saas.commons.exeception.SignatureException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MacSigner extends MacProvider implements ReactiveSigner {

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
	public Mono<byte[]> sign(byte[] data) throws SignatureException {

		return Mono.fromCallable(() -> {
			Mac mac = getMacInstance();
			return mac.doFinal(data);
		}).subscribeOn(Schedulers.boundedElastic())
				.onErrorMap(e -> {
					if (e instanceof NoSuchAlgorithmException || e instanceof InvalidKeyException)
						return new SignatureException("Error during signing operation");
					return e;
				});
	}

	@Override
	public Mono<Boolean> isValid(byte[] data, byte[] signature) {
		return this.sign(data)
				.map(macSignature -> MessageDigest.isEqual(macSignature, signature))
				.onErrorReturn(Boolean.FALSE);
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
