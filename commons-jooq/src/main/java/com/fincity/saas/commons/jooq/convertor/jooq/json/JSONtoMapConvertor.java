package com.fincity.saas.commons.jooq.convertor.jooq.json;

import java.io.Serial;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class JSONtoMapConvertor extends JSONtoJacksonConverter<Map> {

	@Serial
	private static final long serialVersionUID = 21794094885780754L;

	public JSONtoMapConvertor() {
		super(Map.class);
	}

	@Override
	protected Map defaultIfError() {
		return Map.of();
	}
}
