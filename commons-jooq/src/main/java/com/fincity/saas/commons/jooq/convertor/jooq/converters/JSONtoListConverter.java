package com.fincity.saas.commons.jooq.convertor.jooq.converters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.jooq.JSON;

public class JSONtoListConverter<T, U> extends AbstractJooqConverter<JSON, List<U>>{

	@Serial
	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unchecked")
	public JSONtoListConverter(Class<List<U>> toType) {
		super(JSON.class, (Class<List<U>>) (Class<?>) List.class);
	}

	@SuppressWarnings("unchecked")
	public JSONtoListConverter(Class<T> fromType, Class<U> toType) {
		super(JSON.class, (Class<List<U>>) (Class<?>) List.class);
	}

	@Override
	protected String toData(JSON databaseObject) {
		if (databaseObject == null) return null;
		return databaseObject.data();
	}

	@Override
	protected JSON toJson(String string) {
		if (string == null) return null;
		return JSON.jsonOrNull(string);
	}

	@Override
	protected List<U> defaultIfError() {
		return new ArrayList<>();
	}

	@Override
	protected JSON valueIfNull() {
		return JSON.jsonOrNull("[]");
	}
}
