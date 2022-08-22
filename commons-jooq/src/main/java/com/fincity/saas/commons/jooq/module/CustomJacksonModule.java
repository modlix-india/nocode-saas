package com.fincity.saas.commons.jooq.module;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;

public class CustomJacksonModule extends SimpleModule {

	private static final long serialVersionUID = -5726049745793085074L;

	public CustomJacksonModule(AbstractMessageService messageResourceService) {
		super();

		this.addDeserializer(LocalDateTime.class, new StdDeserializer<LocalDateTime>((Class<?>) null) {

			private static final long serialVersionUID = 4146504589335966256L;

			@Override
			public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

				long inst = p.getValueAsLong();
				return LocalDateTime.ofInstant(Instant.ofEpochMilli(inst), ZoneId.of("UTC"));
			}
		});

		this.addSerializer(LocalDateTime.class, new StdSerializer<LocalDateTime>((Class<LocalDateTime>) null) {

			private static final long serialVersionUID = -3480737241961681306L;

			@Override
			public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
			        throws IOException {

//				provider.get
				gen.writeNumber(value.toEpochSecond(ZoneOffset.UTC));
			}
		});

		this.addDeserializer(ULong.class, new UNumberDeserializer<>(ULong.class, messageResourceService));
		this.addDeserializer(UShort.class, new UNumberDeserializer<>(UShort.class, messageResourceService));
		this.addDeserializer(UInteger.class, new UNumberDeserializer<>(UInteger.class, messageResourceService));

		this.addSerializer(new UNumberSerializer());
	}
}
