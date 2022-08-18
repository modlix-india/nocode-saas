package com.fincity.security.module;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jooq.types.UNumber;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fincity.security.exception.GenericException;
import com.fincity.security.service.MessageResourceService;

public class UNumberDeserializer<R extends UNumber> extends StdDeserializer<R> {

	private static final long serialVersionUID = -2888640386444756529L;

	private final Class<R> classs;

	private transient Method method;

	private transient MessageResourceService msgResource;

	public UNumberDeserializer(Class<R> classs, MessageResourceService msgResource) {
		super((Class<?>) null);
		this.classs = classs;
		try {
			this.method = this.classs.getDeclaredMethod("valueOf", String.class);
		} catch (Exception e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
			        msgResource.getDefaultLocaleMessage(MessageResourceService.VALUEOF_METHOD_NOT_FOUND));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public R deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

		String str = p.getValueAsString();

		try {
			return (R) this.method.invoke(null, str);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
			        msgResource.getDefaultLocaleMessage(MessageResourceService.UNABLE_TO_CONVERT, str, this.classs.getSimpleName()));
		}
	}

}
