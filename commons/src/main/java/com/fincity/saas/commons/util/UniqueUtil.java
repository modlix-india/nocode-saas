package com.fincity.saas.commons.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UniqueUtil {

	public static String base36UUID() {

		UUID uuid = UUID.randomUUID();
		long l = ByteBuffer.wrap(uuid.toString()
		        .getBytes())
		        .getLong();

		return Long.toString(l, 0x24);
	}
	
	public static String uniqueName(int maxLength, String... name) { //NOSONAR

		StringBuilder sb = new StringBuilder(maxLength);

		int i = 0;
		maxLength -= 15;

		for (String str : name) { //NOSONAR

			if (i > maxLength)
				break;
			
			if (str == null)
				continue;

			for (Character chr : str.toCharArray()) {

				if (i > maxLength)
					break;

				Character lChr = Character.toLowerCase(chr);
				if ((lChr >= 'a' && lChr <= 'z') || lChr == '_' || (lChr >='0' && lChr <= '9')) {
					sb.append(lChr);
					i++;
				}
			}

			sb.append('_');
		}

		return sb.append(base36UUID())
		        .toString();
	}

	private UniqueUtil() {
	}
}
