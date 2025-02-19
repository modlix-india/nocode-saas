package com.fincity.saas.commons.jooq.convertor.jooq.json;

import java.io.Serial;

import org.jooq.JSON;

public class JSONtoJacksonConverter<U> extends AbstractToJacksonConverter<JSON, U> {

	@Serial
	private static final long serialVersionUID = 4084897018025032842L;

	public JSONtoJacksonConverter(Class<U> toType) {
		super(JSON.class, toType);
	}

	@Override
	public String data(JSON json) {
		return json.data();
	}

	@Override
	public JSON json(String string) {
		return JSON.jsonOrNull(string);
	}

	@Override
	protected U defaultIfError() {
		return null;
	}
}
