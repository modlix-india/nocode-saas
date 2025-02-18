package com.fincity.saas.notification.util.converter;

import org.springframework.core.convert.converter.Converter;

public class EnumToStringConverter<T extends Enum<T>> implements Converter<T, String> {

	@Override
	public String convert(T value) {
		return value.name();
	}
}
