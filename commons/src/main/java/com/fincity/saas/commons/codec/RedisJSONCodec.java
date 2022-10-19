package com.fincity.saas.commons.codec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;

import io.lettuce.core.codec.RedisCodec;

public class RedisJSONCodec implements RedisCodec<String, Object> {

	private ObjectMapper objectMapper;

	public String decodeKey(ByteBuffer bytes) {
		return StandardCharsets.UTF_8.decode(bytes)
		        .toString();
	}

	public Object decodeValue(ByteBuffer bytes) {

		byte[] array = new byte[bytes.remaining()];
		bytes.get(array);

		String jsonString = new String(array);

		try {

			Map<String, Object> map = this.objectMapper.readValue(array, new TypeReference<Map<String, Object>>() {
			});

			return this.objectMapper.convertValue(map.get("value"), Class.forName(map.get("classType")
			        .toString()));

		} catch (Exception e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot retrive Object from : " + jsonString,
			        e);
		}
	}

	public ByteBuffer encodeKey(String key) {
		return StandardCharsets.UTF_8.encode(key);
	}

	public ByteBuffer encodeValue(Object value) {

		Map<String, Object> map = new HashMap<>();
		map.put("classType", value.getClass()
		        .getName());
		map.put("value", value);

		try {
			return ByteBuffer.wrap(this.objectMapper.writeValueAsString(map)
			        .getBytes());
		} catch (JsonProcessingException e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot convert value to json : " + value, e);
		}
	}

	public RedisJSONCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}
}