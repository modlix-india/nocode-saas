package com.modlix.saas.commons2.jooq.jackson;

import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.modlix.saas.commons2.configuration.service.AbstractMessageService;

public class UnsignedNumbersSerializationModule extends SimpleModule {

	private static final long serialVersionUID = -5726049745793085074L;

	public UnsignedNumbersSerializationModule(AbstractMessageService messageResourceService) {
		super();

		this.addDeserializer(ULong.class, new UNumberDeserializer<>(ULong.class, messageResourceService));
		this.addDeserializer(UShort.class, new UNumberDeserializer<>(UShort.class, messageResourceService));
		this.addDeserializer(UInteger.class, new UNumberDeserializer<>(UInteger.class, messageResourceService));
		this.addSerializer(new UNumberSerializer());
	}
}

