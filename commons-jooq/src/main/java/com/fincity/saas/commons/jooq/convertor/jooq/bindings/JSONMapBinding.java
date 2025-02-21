package com.fincity.saas.commons.jooq.convertor.jooq.bindings;

import java.util.Map;

import org.jooq.JSON;
import org.jooq.impl.SQLDataType;

import com.fincity.saas.commons.jooq.convertor.jooq.converters.JSONtoClassConverter;

@SuppressWarnings("rawtypes")
public class JSONMapBinding extends AbstractJooqBinding<JSON, Map> {

	public JSONMapBinding() {
		super(new JSONtoClassConverter<>(Map.class), SQLDataType.JSON.getSQLType());
	}
}
