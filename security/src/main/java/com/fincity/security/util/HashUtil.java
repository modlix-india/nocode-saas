package com.fincity.security.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HashUtil {

	private static final char[] hexCode = "0123456789abcdef".toCharArray();

	private HashUtil() {
	}

	public static String hash(String key) {
		return hash(key, HashAlgorithm.SHA256);
	}

	public static String hash(String key, HashAlgorithm algorithm) {
		byte[] hash = algorithm.hash(key.getBytes(StandardCharsets.UTF_8));
		return bytesToHex(hash);
	}

	public static boolean equal(String hashedString, String nonHashedString) {
		return equal(hashedString, nonHashedString, HashAlgorithm.SHA256);
	}

	public static boolean equal(String hashedString, String nonHashedString, HashAlgorithm algorithm) {
		if (hashedString == null || nonHashedString == null) {
			return false;
		}

		if (hashedString.length() != algorithm.getHexLength()) {
			return false;
		}

		String newHash = hash(nonHashedString, algorithm);
		return MessageDigest.isEqual(hashedString.getBytes(StandardCharsets.UTF_8),
				newHash.getBytes(StandardCharsets.UTF_8)
		);
	}

	public static String hashMultiple(String... keys) {
		return Arrays.stream(keys).map(key -> hash(key) + "\n").collect(Collectors.joining());
	}

	private static String bytesToHex(byte[] data) {

		StringBuilder r = new StringBuilder(data.length * 2);

		for (final byte b : data) r.append(hexCode[b >> 4 & 15]).append(hexCode[b & 15]);

		return r.toString();
	}

}
