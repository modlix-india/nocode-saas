package com.fincity.saas.commons.jooq.convertor.jooq.bindings;

import java.util.Map;

import org.jooq.JSON;
import org.jooq.impl.SQLDataType;

import com.fincity.saas.commons.jooq.convertor.jooq.converters.JSONtoMapConvertor;

@SuppressWarnings("rawtypes")
public class JSONMapBinding extends AbstractJooqBinding<JSON, Map> {

	public JSONMapBinding() {
		super(new JSONtoMapConvertor(), SQLDataType.JSON.getSQLType());
	}
}
