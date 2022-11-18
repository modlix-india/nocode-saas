package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

import java.util.List;
import java.util.Objects;

import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.PastPassword;
import com.fincity.security.jooq.tables.records.SecurityPastPasswordsRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PastPasswordDAO extends AbstractDAO<SecurityPastPasswordsRecord, ULong, PastPassword> {

	protected PastPasswordDAO(Class<PastPassword> pojoClass, Table<SecurityPastPasswordsRecord> table,
	        Field<ULong> idField) {
		super(pojoClass, table, idField);
	}

	public Mono<List<String>> fetchPasswordsById(ULong userId) {

		return Flux.from(this.dslContext.select(SECURITY_PAST_PASSWORDS.PASSWORD)
		        .from(SECURITY_PAST_PASSWORDS)
		        .where(SECURITY_PAST_PASSWORDS.USER_ID.eq(userId))
		        .orderBy(SECURITY_PAST_PASSWORDS.CREATED_AT.asc()))
		        .map(Record1::value1)
		        .collectList();
	}

	public Mono<Boolean> deletePastPasswordBasedOnUserId(ULong userId) {

		Mono<ULong> deletableId = Mono.from(this.dslContext.select(SECURITY_PAST_PASSWORDS.ID)
		        .from(SECURITY_PAST_PASSWORDS)
		        .where(SECURITY_PAST_PASSWORDS.USER_ID.eq(userId))
		        .orderBy(SECURITY_PAST_PASSWORDS.CREATED_AT.asc())
		        .limit(1))
		        .map(Record1::value1);

		DeleteQuery<SecurityPastPasswordsRecord> query = this.dslContext.deleteQuery(SECURITY_PAST_PASSWORDS);

		return deletableId.map(id -> {
			query.addConditions(SECURITY_PAST_PASSWORDS.ID.eq(id));
			return Mono.from(query);
		})
		        .map(Objects::nonNull);
	}

	public Mono<Integer> fetchPasswordsCount(ULong userId) {

		return Mono.from(this.dslContext.selectCount()
		        .from(SECURITY_PAST_PASSWORDS)
		        .where(SECURITY_PAST_PASSWORDS.USER_ID.eq(userId)))
		        .map(Record1::value1);
	}

}
