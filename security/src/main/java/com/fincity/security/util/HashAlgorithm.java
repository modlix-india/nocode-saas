package com.fincity.security.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;

import lombok.Getter;

@Getter
public enum HashAlgorithm {

	MD2("MD2", 128),
	MD5("MD5", 128),
	SHA1("SHA-1", 160),
	SHA224("SHA-224", 224),
	SHA256("SHA-256", 256),
	SHA384("SHA-384", 384),
	SHA512("SHA-512", 512),
	SHA512_224("SHA-512/224", 224),
	SHA512_256("SHA-512/256", 256),
	SHA3_224("SHA3-224", 224),
	SHA3_256("SHA3-256", 256),
	SHA3_384("SHA3-384", 384),
	SHA3_512("SHA3-512", 512);

	private final String algorithmName;
	private final int bits;

	HashAlgorithm(String algorithmName, int bits) {
		this.algorithmName = algorithmName;
		this.bits = bits;
	}

	public int getHexLength() {
		return this.bits / 4;
	}

	public byte[] hash(byte[] input) {
		try {
			MessageDigest digest = MessageDigest.getInstance(algorithmName);
			return digest.digest(input);
		} catch (NoSuchAlgorithmException e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
