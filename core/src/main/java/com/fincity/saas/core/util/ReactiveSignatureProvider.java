package com.fincity.saas.core.util;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.lang.RuntimeEnvironment;
import reactor.core.publisher.Mono;

public abstract class ReactiveSignatureProvider {

	public static final SecureRandom DEFAULT_SECURE_RANDOM;

	static {
		DEFAULT_SECURE_RANDOM = new SecureRandom();
		DEFAULT_SECURE_RANDOM.nextBytes(new byte[64]);
	}

	protected final SignatureAlgorithm alg;
	protected final Key key;

	protected ReactiveSignatureProvider(SignatureAlgorithm alg, Key key) {
		this.alg = alg;
		this.key = key;
	}

	protected Mono<Signature> createSignatureInstance(){

		try {
			return getSignatureInstance();
		} catch (NoSuchAlgorithmException e) {
			throw new GenericException(HttpStatus.NOT_FOUND, StringFormatter.format("Unavailable $ : Signature algorithm : '$'.", alg.getFamilyName(), alg.getJcaName()));
		}
	}

	protected Mono<Signature> getSignatureInstance() throws NoSuchAlgorithmException {
		return Mono.just(Signature.getInstance(alg.getJcaName()));
	}

	protected boolean isBouncyCastleAvailable() {
		return RuntimeEnvironment.BOUNCY_CASTLE_AVAILABLE;
	}

}
