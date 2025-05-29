package com.fincity.saas.commons.jooq.convertor.gson;

import java.io.IOException;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class ULongAdapter extends TypeAdapter<ULong> {
	@Override
	public void write(JsonWriter out, ULong value) throws IOException {
		if (value == null) {
			out.nullValue();
			return;
		}
		out.value(value.toString());
	}

	@Override
	public ULong read(JsonReader in) throws IOException {
		JsonToken token = in.peek();
		if (token == JsonToken.NULL) {
			in.nextNull();
			return null;
		}

		String value = in.nextString();
		return ULongUtil.valueOf(value);
	}
}
