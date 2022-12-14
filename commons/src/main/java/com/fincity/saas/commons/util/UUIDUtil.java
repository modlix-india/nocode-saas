package com.fincity.saas.commons.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class UUIDUtil {

	public static String base36UUID() {

		UUID uuid = UUID.randomUUID();
		long l = ByteBuffer.wrap(uuid.toString()
		        .getBytes())
		        .getLong();

		return Long.toString(l, 0x24);
	}

	private UUIDUtil() {
	}
}
