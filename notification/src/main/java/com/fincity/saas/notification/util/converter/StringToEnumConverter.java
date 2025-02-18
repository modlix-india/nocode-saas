package com.fincity.saas.notification.util.converter;

import org.springframework.core.convert.converter.Converter;

public class StringToEnumConverter<T extends Enum<T>> implements Converter<String, T> {

	private final Class<T> enumType;

	public StringToEnumConverter(Class<T> enumType) {
		this.enumType = enumType;
	}

	@Override
	public T convert(String source) {
		try {
			return Enum.valueOf(enumType, source); // Convert String to Enum
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid enum value: " + source + " for type: " + enumType.getSimpleName());
		}
	}

}
