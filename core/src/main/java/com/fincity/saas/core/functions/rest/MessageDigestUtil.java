package com.fincity.saas.core.functions.rest;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MessageDigestUtil {

	public static String generateDigest(String data, String secretKey, String algorithm)
			throws NoSuchAlgorithmException, InvalidKeyException {

		Mac mac = Mac.getInstance(algorithm);

		SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), algorithm);
		mac.init(secretKeySpec);

		byte[] hmacBytes = mac.doFinal(data.getBytes());

		return java.util.Base64.getEncoder().encodeToString(hmacBytes);
	}

	public static boolean verifyDigest(String data, String secretKey, String algorithm, String signature)
			throws NoSuchAlgorithmException, InvalidKeyException {

		String generatedSignature = generateDigest(data, secretKey, algorithm);

		return generatedSignature.equals(signature);
	}


}
