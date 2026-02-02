package com.modlix.saas.commons2.jooq.jackson;

import java.io.IOException;

import org.jooq.types.UNumber;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class UNumberSerializer extends StdSerializer<UNumber> {

	private static final long serialVersionUID = -2888640386444756529L;

	public UNumberSerializer() {
		super(UNumber.class);
	}

	@Override
	public void serialize(UNumber value, JsonGenerator gen, SerializerProvider provider) throws IOException {

		gen.writeNumber(value.toBigInteger());
	}

}

