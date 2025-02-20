package com.fincity.saas.commons.jooq.convertor.jooq.converters;

import java.io.Serial;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class JSONtoMapConvertor extends JSONtoClassConverter<Map> {

	@Serial
	private static final long serialVersionUID = 21794094885780754L;

	public JSONtoMapConvertor() {
		super(Map.class);
	}
}
