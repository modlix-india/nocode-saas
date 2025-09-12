package com.modlix.saas.commons2.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.UUID;

public class UniqueUtil {

	private static final String BASE = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	public static String shortUUID() {

		UUID uuid = UUID.randomUUID();

		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
		buffer.putLong(uuid.getMostSignificantBits());
		buffer.putLong(uuid.getLeastSignificantBits());

		BigInteger num = new BigInteger(buffer.array());
		StringBuilder sb = new StringBuilder();
		BigInteger baseDivisor = new BigInteger("" + BASE.length());

		while (num.compareTo(BigInteger.ZERO) != 0) {
			sb.append(BASE.charAt(num.mod(baseDivisor)
			        .intValue()));
			num = num.divide(baseDivisor);
		}

		return String.format("%22s", sb.reverse()
		        .toString())
		        .replace(' ', '0');
	}

	public static String base36UUID() {

		UUID uuid = UUID.randomUUID();
		long l = ByteBuffer.wrap(uuid.toString()
		        .getBytes())
		        .getLong();

		return Long.toString(l, 0x24);
	}

	public static String uniqueName(int maxLength, String... name) { // NOSONAR

		StringBuilder sb = new StringBuilder(maxLength);

		int i = 0;
		maxLength -= 15;

		for (String str : name) { // NOSONAR

			if (i > maxLength)
				break;

			if (str == null)
				continue;

			for (Character chr : str.toCharArray()) {

				if (i > maxLength)
					break;

				Character lChr = Character.toLowerCase(chr);
				if ((lChr >= 'a' && lChr <= 'z') || lChr == '_' || (lChr >= '0' && lChr <= '9')) {
					sb.append(lChr);
					i++;
				}
			}

			sb.append('_');
		}

		return sb.append(base36UUID())
		        .toString();
	}

	public static String uniqueNameOnlyLetters(int maxLength, String... name) { // NOSONAR

		StringBuilder sb = new StringBuilder(maxLength);

		int i = 0;
		maxLength -= 15;

		String[] arr = name.length == 0 ? new String[2] : new String[name.length + 2];
		System.arraycopy(name, 0, arr, 0, name.length);
		arr[name.length] = base36UUID();
		arr[name.length+1] = base36UUID();

		for (String str : arr) { // NOSONAR

			if (i > maxLength)
				break;

			if (str == null)
				continue;

			for (Character chr : str.toCharArray()) {

				if (i > maxLength)
					break;

				Character lChr = Character.toLowerCase(chr);
				if ((lChr >= 'a' && lChr <= 'z')) {
					sb.append(lChr);
					i++;
				}
			}
		}

		return sb.toString();
	}

	private UniqueUtil() {
	}
}
