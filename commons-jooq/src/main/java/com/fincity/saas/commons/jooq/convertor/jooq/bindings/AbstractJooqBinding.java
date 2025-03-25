package com.fincity.saas.commons.jooq.convertor.jooq.bindings;

import java.sql.SQLException;

import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.impl.AbstractBinding;

import lombok.NonNull;

/**
 * Abstract JOOQ binding implementation to handle custom type conversions
 * between a database type (T) and a Java type (U).
 *
 * <p>
 * This class uses a {@link Converter} to transform types and delegates resource
 * management (e.g., {@link java.sql.ResultSet}, {@link java.sql.PreparedStatement})
 * to JOOQ.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> This class does not use auto-closeable resources such
 * as {@link java.sql.ResultSet} or {@link java.sql.PreparedStatement} because
 * JOOQ handles resource management internally. Introducing explicit resource
 * management here could lead to conflicts or early closure of resources
 * that JOOQ is actively managing.
 * </p>
 *
 * @param <T> The database column type.
 * @param <U> The custom Java type to which T is converted.
 */
public abstract class AbstractJooqBinding<T, U> extends AbstractBinding<T, U> {

	private final Converter<T, U> converter;

	private final int targetsSqlType;

	protected AbstractJooqBinding(Converter<T, U> converter, int targetsSqlType) {
		this.converter = converter;
		this.targetsSqlType = targetsSqlType;
	}

	public  String getConvertStringValue(T value) {
		return value.toString();
	}

	@NonNull
	@Override
	public Converter<T, U> converter() {
		return converter;
	}

	@Override
	public void set(BindingSetStatementContext<U> ctx) throws SQLException {
		T value = ctx.convert(converter()).value();
		ctx.statement().setObject(ctx.index(), this.getConvertStringValue(value), targetsSqlType); // NOSONAR
	}

	@Override
	public void get(BindingGetResultSetContext<U> ctx) throws SQLException {
		if (ctx.resultSet().wasNull()) { // NOSONAR
			ctx.convert(converter()).value(null);
			return;
		}

		T value = ctx.resultSet().getObject(ctx.index(), converter().fromType()); // NOSONAR
		ctx.convert(converter()).value(value);
	}
}
