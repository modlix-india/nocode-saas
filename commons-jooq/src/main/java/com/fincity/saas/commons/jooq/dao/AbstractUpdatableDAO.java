package com.fincity.saas.commons.jooq.dao;

import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.springframework.transaction.annotation.Transactional;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Transactional
public abstract class AbstractUpdatableDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>>
        extends AbstractDAO<R, I, D> {

	private static final String UPDATED_BY = "UPDATED_BY";

	protected final Field<?> updatedByField;

	protected AbstractUpdatableDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {
		super(pojoClass, table, idField);
		this.updatedByField = table.field(UPDATED_BY);
	}

	public <A extends AbstractUpdatableDTO<I, I>> Mono<D> update(A entity) {

		entity.setUpdatedAt(null);
		UpdatableRecord<R> rec = this.dslContext.newRecord(this.table);
		rec.from(entity);
		rec.reset("CREATED_BY");
		rec.reset("CREATED_AT");
		return Mono.from(this.dslContext.update(this.table)
		        .set(rec)
		        .where(this.idField.eq(entity.getId())))
		        .thenReturn(rec.into(this.pojoClass));
	}

	public Mono<D> update(I id, Map<String, Object> updateFields) {

		if (updateFields.containsKey("createdAt"))
			updateFields.remove("createdAt");

		Map<Field<?>, Object> fields = updateFields.entrySet()
		        .stream()
		        .map(e -> Tuples.of(this.getField(e.getKey()), e.getValue()))
		        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));

		return Mono.from(this.dslContext.update(this.table)
		        .set(fields)
		        .where(this.idField.eq(id)))
		        .then(this.readById(id));
	}
}
