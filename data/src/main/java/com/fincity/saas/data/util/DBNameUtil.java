package com.fincity.saas.data.util;

import static com.fincity.saas.commons.util.UniqueUtil.base36UUID;

public class DBNameUtil {

	public static String uniqueName(int maxLength, String... name) {

		StringBuilder sb = new StringBuilder(maxLength);

		int i = 0;
		maxLength -= 15;

		for (String str : name) {

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

	private DBNameUtil() {
	}
}
