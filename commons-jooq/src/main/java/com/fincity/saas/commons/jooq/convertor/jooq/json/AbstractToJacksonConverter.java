package com.fincity.saas.commons.jooq.convertor.jooq.json;

import org.jooq.impl.AbstractConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.jackson.TupleSerializationModule;

public abstract class AbstractToJacksonConverter<J, U> extends AbstractConverter<J, U> {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractToJacksonConverter.class);
	protected final ObjectMapper mapper;

	protected AbstractToJacksonConverter(Class<J> fromType, Class<U> toType) {
		super(fromType, toType);
		mapper = JsonMapper
				.builder()
				.addModule(new CommonsSerializationModule())
				.addModule(new TupleSerializationModule())
				.build();
	}

	protected abstract String data(J json);

	protected abstract J json(String string);

	protected abstract U defaultIfError();

	@Override
	public U from(J databaseObject) {
		if (databaseObject == null)
			return null;

		try {
			return mapper.readValue(data(databaseObject), toType());
		} catch (JsonProcessingException e) {
			logger.error("Error when converting JSON to {}", toType(), e);
			return defaultIfError();
		}
	}

	@Override
	public J to(U userObject) {
		if (userObject == null)
			return null;

		try {
			return json(mapper.writeValueAsString(userObject));
		} catch (JsonProcessingException e) {
			logger.error("Error when converting object of type " + toType() + " to JSON", e);
			return json(null);
		}
	}
}
