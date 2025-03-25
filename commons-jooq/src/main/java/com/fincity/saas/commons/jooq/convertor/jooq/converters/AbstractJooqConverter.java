package com.fincity.saas.commons.jooq.convertor.jooq.converters;

import org.jooq.impl.AbstractConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.jackson.TupleSerializationModule;

public abstract class AbstractJooqConverter<J, U> extends AbstractConverter<J, U> {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractJooqConverter.class);

	protected final ObjectMapper mapper;

	protected AbstractJooqConverter(Class<J> fromType, Class<U> toType) {
		super(fromType, toType);
		mapper = JsonMapper
				.builder()
				.addModule(new CommonsSerializationModule())
				.addModule(new TupleSerializationModule())
				.build();
	}

	protected abstract String toData(J databaseObject);

	protected abstract J toJson(String string);

	protected abstract U defaultIfError();

	protected J valueIfNull() {
		return null;
	}

	@Override
	public U from(J databaseObject) {
		if (databaseObject == null)
			return null;

		try {
			String data = this.toData(databaseObject);
			return mapper.readValue(data, toType());
		} catch (Exception e) {
			logger.error("Error when converting JSON to {}", toType(), e);
			return defaultIfError();
		}
	}

	@Override
	public J to(U userObject) {
		if (userObject == null)
			return this.valueIfNull();

		try {
			String jsonString = mapper.writeValueAsString(userObject);
			return this.toJson(jsonString);
		} catch (Exception e) {
			logger.error("Error when converting object of type {} to JSON", toType(), e);
			return this.valueIfNull();
		}
	}
}
