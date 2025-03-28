package com.fincity.saas.notification.dao;

import java.io.Serializable;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Mono;

public abstract class AbstractCodeDao<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>>
		extends AbstractUpdatableDAO<R, I, D> {

	private final Field<String> codeField;

	protected AbstractCodeDao(Class<D> pojoClass, Table<R> table, Field<I> idField, Field<String> codeField) {
		super(pojoClass, table, idField);
		this.codeField = codeField;
	}

	public Mono<D> getByCode(String code) {
		return Mono.from(
				this.dslContext.selectFrom(this.table).where(codeField.eq(code)))
				.map(result -> result.into(this.pojoClass));
	}
}
