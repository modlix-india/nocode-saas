package com.fincity.saas.commons.jackson;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fincity.saas.commons.model.condition.AbstractCondition;

public class CommonsSerializationModule extends SimpleModule {

	private static final long serialVersionUID = 6242981337057158018L;

	public CommonsSerializationModule() {

		super();

		this.addDeserializer(LocalDateTime.class, new StdDeserializer<LocalDateTime>((Class<?>) null) {

			private static final long serialVersionUID = 4146504589335966256L;

			@Override
			public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

				long inst = p.getValueAsLong();
				return LocalDateTime.ofEpochSecond(inst, 0, ZoneOffset.UTC);
			}
		});

		this.addSerializer(LocalDateTime.class, new StdSerializer<LocalDateTime>((Class<LocalDateTime>) null) {

			private static final long serialVersionUID = -3480737241961681306L;

			@Override
			public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
					throws IOException {

				gen.writeNumber(value.toEpochSecond(ZoneOffset.UTC));
			}
		});

		this.addDeserializer(AbstractCondition.class, new AbstractCondtionDeserializer());
	}
}
