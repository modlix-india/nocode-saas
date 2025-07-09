package com.fincity.saas.commons.jooq.gson;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.jooq.types.UNumber;

public class UNumberListAdapter<R extends UNumber> extends AbstractListAdapter<R>{

	protected UNumberListAdapter(Function<String, R> deserializer) {
		super(deserializer);
	}

	public UNumberListAdapter(Class<R> clazz) {
		super(value -> {
			try {
				Method method = clazz.getDeclaredMethod("valueOf", String.class);
				return (R) method.invoke(null, value);
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Unable to convert " + value + " to " + clazz.getSimpleName(), e);
			}
		});
	}

	@Override
	protected String serializeItem(R item) {
		return item.toString();
	}
}
