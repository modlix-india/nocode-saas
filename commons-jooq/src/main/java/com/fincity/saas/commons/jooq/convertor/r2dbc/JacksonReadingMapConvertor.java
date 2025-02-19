package com.fincity.saas.commons.jooq.convertor.r2dbc;

import java.util.LinkedHashMap;

import org.springframework.data.convert.ReadingConverter;

import com.fasterxml.jackson.core.JsonProcessingException;

@ReadingConverter
@SuppressWarnings("rawtypes")
public class JacksonReadingMapConvertor extends AbstractSpringConverter<String, LinkedHashMap> {

	public JacksonReadingMapConvertor() {
		super(String.class, LinkedHashMap.class);
	}

	@Override
	public LinkedHashMap convert(String source) {
		try {
			return mapper.readValue(source, LinkedHashMap.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error converting JSON string to LinkedHashMap", e);
		}
	}
}
