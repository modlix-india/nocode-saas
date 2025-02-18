package com.fincity.saas.commons.jooq.convertor.json;

import org.jooq.exception.DataTypeException;
import org.jooq.impl.AbstractConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public abstract class AbstractToJacksonConverter<J, U> extends AbstractConverter<J, U> {

	final ObjectMapper mapper;

	public AbstractToJacksonConverter(Class<J> fromType, Class<U> toType) {
		super(fromType, toType);
		mapper = JsonMapper.builder().build();
	}

	abstract String data(J json);

	abstract J json(String string);

	@Override
	public U from(J databaseObject) {
		if (databaseObject == null)
			return null;

		try {
			return mapper.readValue(data(databaseObject), toType());
		}
		catch (JsonProcessingException e) {
			throw new DataTypeException("Error when converting JSON to " + toType(), e);
		}
	}

	@Override
	public J to(U userObject) {
		return null;
	}
}
