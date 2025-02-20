package com.fincity.saas.commons.jooq.convertor.r2dbc;

import java.util.Map;

import org.springframework.data.convert.ReadingConverter;

import com.fasterxml.jackson.core.JsonProcessingException;

@ReadingConverter
@SuppressWarnings("rawtypes")
public class JacksonReadingMapConvertor extends AbstractSpringConverter<String, Map> {

	public JacksonReadingMapConvertor() {
		super(String.class, Map.class);
	}

	@Override
	public Map convert(String source) {
		try {
			return mapper.readValue(source, Map.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error converting JSON string to LinkedHashMap", e);
		}
	}
}
