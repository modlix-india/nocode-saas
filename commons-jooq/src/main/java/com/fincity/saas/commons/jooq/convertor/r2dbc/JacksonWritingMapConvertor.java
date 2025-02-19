package com.fincity.saas.commons.jooq.convertor.r2dbc;

import java.util.LinkedHashMap;

import org.springframework.data.convert.WritingConverter;

import com.fasterxml.jackson.core.JsonProcessingException;

@WritingConverter
@SuppressWarnings("rawtypes")
public class JacksonWritingMapConvertor extends AbstractSpringConverter<LinkedHashMap, String> {

	public JacksonWritingMapConvertor() {
		super(LinkedHashMap.class, String.class);
	}

	@Override
	public String convert(LinkedHashMap source) {
		try {
			return mapper.writeValueAsString(source);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error converting JSON string to LinkedHashMap", e);
		}
	}
}
