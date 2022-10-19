package com.fincity.saas.commons.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.lettuce.core.codec.RedisCodec;

public class RedisObjectCodec implements RedisCodec<String, Object> {

	public String decodeKey(ByteBuffer bytes) {
		return StandardCharsets.UTF_8.decode(bytes)
		        .toString();
	}

	public Object decodeValue(ByteBuffer bytes) {
		try {
			byte[] array = new byte[bytes.remaining()];
			bytes.get(array);
			ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(array));
			return is.readObject();
		} catch (Exception e) {
			return null;
		}
	}

	public ByteBuffer encodeKey(String key) {
		return StandardCharsets.UTF_8.encode(key);
	}

	public ByteBuffer encodeValue(Object value) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(bytes);
			os.writeObject(value);
			return ByteBuffer.wrap(bytes.toByteArray());
		} catch (IOException e) {
			return null;
		}
	}

}