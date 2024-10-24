package com.fincity.security.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;


public class HashUtil {

	private static final char[] hexCode = "0123456789abcdef".toCharArray();

	private HashUtil() {
	}

	public static String hash(String key) {

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(key.getBytes());
			return bytesToHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to find a hashing algorithm.");
		}

	}

	public static String hashMultiple(String... keys) {
		return Arrays.stream(keys).map(key -> hash(key) + "\n").collect(Collectors.joining());
	}

	private static String bytesToHex(byte[] data) {

		StringBuilder r = new StringBuilder(data.length * 2);

		for (final byte b : data) r.append(hexCode[b >> 4 & 15]).append(hexCode[b & 15]);

		return r.toString();
	}

	public static void main(String[] args) {
		System.out.println(hashMultiple("asdoiajdoij", "hello world"));
	}

}
