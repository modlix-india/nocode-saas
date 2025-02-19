package com.fincity.saas.commons.jooq.convertor.r2dbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.jackson.TupleSerializationModule;

public abstract class AbstractSpringConverter<S, T> implements Converter<S, T> {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractSpringConverter.class);
	protected final ObjectMapper mapper;

	private final Class<S> sourceType;

	private final Class<T> toType;

	protected AbstractSpringConverter(Class<S> sourceType, Class<T> toType) {
		this.sourceType = sourceType;
		this.toType = toType;
		this.mapper = JsonMapper
				.builder()
				.addModule(new CommonsSerializationModule())
				.addModule(new TupleSerializationModule())
				.build();
	}

	public final Class<S> sourceType() {
		return sourceType;
	}

	public final Class<T> toType() {
		return toType;
	}
}
