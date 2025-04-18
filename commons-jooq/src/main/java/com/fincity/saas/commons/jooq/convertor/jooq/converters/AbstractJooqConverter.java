package com.fincity.saas.commons.jooq.convertor.jooq.converters;

import org.jooq.impl.AbstractConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public abstract class AbstractJooqConverter<T, U> extends AbstractConverter<T, U> {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractJooqConverter.class);

	protected final ObjectMapper objectMapper = new ObjectMapper()
		.registerModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	protected AbstractJooqConverter(Class<T> fromType, Class<U> toType) {
		super(fromType, toType);
	}

	protected abstract String toData(T databaseObject);

	protected abstract T toJson(String string);

	protected abstract U defaultIfError();

	protected T valueIfNull() {
		return null;
	}

	@Override
	public U from(T databaseObject) {
		if (databaseObject == null)
			return null;

		try {
			String data = this.toData(databaseObject);
			return objectMapper.readValue(data, toType());
		} catch (Exception e) {
			logger.error("Error when converting JSON to {}", toType(), e);
			return defaultIfError();
		}
	}

	@Override
	public T to(U userObject) {
		if (userObject == null)
			return this.valueIfNull();

		try {
			String jsonString = objectMapper.writeValueAsString(userObject);
			return this.toJson(jsonString);
		} catch (Exception e) {
			logger.error("Error when converting object of type {} to JSON", toType(), e);
			return this.valueIfNull();
		}
	}
}
