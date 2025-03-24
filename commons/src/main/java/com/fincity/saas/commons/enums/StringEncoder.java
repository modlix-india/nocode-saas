package com.fincity.saas.commons.enums;

import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import lombok.Getter;

@Getter
public enum StringEncoder {

	BASE64("base64"),
	HEX("Hex");

	private static final Map<String, StringEncoder> BY_NAME = new HashMap<>();

	static {
		for (StringEncoder encoder : values()) {
			BY_NAME.put(encoder.name.toLowerCase(), encoder);
		}
	}

	private final String name;

	StringEncoder(String name) {
		this.name = name;
	}

	public static StringEncoder getByName(String name) {
		return BY_NAME.get(name.toLowerCase());
	}

	public static List<JsonElement> getAvailableEncoder(StringEncoder... encoder) {

		List<StringEncoder> selected = (encoder == null || encoder.length == 0) ? List.of(StringEncoder.values())
				: List.of(encoder);

		return selected.stream().map(algo -> new JsonPrimitive(algo.getName())).collect(Collectors.toList());
	}

	public JsonPrimitive encodeToJson(byte[] bytes) {
		return new JsonPrimitive(encode(bytes));
	}

	public String encode(byte[] bytes) {
		return switch (this) {
			case BASE64 -> encodeBase64(bytes);
			case HEX -> encodeHex(bytes);
		};
	}

	public byte[] decode(String str) {
		return switch (this) {
			case BASE64 -> decodeBase64(str);
			case HEX -> decodeHex(str);
		};
	}

	private String encodeBase64(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

	private String encodeHex(byte[] bytes) {
		return HexFormat.of().formatHex(bytes);
	}

	private byte[] decodeBase64(String str) {
		return Base64.getDecoder().decode(str);
	}

	private byte[] decodeHex(String str) {
		return HexFormat.of().parseHex(str);
	}
}
