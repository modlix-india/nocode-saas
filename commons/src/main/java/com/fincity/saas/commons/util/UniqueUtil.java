package com.fincity.saas.commons.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

public class UniqueUtil {

	private static final String BASE = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	/** Crockford base32: no I, L, O or U, so a ULID cannot be misread aloud or mistyped. */
	private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int ULID_LENGTH = 26;

	private static final int ULID_TIME_CHARS = 10;

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

	/**
	 * A ULID: 48 bits of millisecond timestamp then 80 bits of randomness, Crockford
	 * base32, 26 characters, lexicographically sortable by creation time.
	 *
	 * This is the row id for app data on a relational backend. An auto-increment key
	 * would be wrong on three counts: sequential ids are enumerable and per-row
	 * authorization does not exist, the caller can supply its own id before an insert
	 * (MongoAppDataService.create takes one from the payload) which a sequence cannot
	 * support, and a numeric id would give _id two different shapes depending on which
	 * backend a storage happens to sit on.
	 */
	public static String ulid() {

		char[] out = new char[ULID_LENGTH];

		long time = System.currentTimeMillis();
		for (int i = ULID_TIME_CHARS - 1; i >= 0; i--) {
			out[i] = CROCKFORD[(int) (time & 0x1f)];
			time >>>= 5;
		}

		for (int i = ULID_TIME_CHARS; i < ULID_LENGTH; i++)
			out[i] = CROCKFORD[RANDOM.nextInt(CROCKFORD.length)];

		return new String(out);
	}

	private UniqueUtil() {
	}
}
