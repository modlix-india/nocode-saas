package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

import java.util.List;

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
		        .where(SECURITY_PAST_PASSWORDS.ID.eq(userId)))
		        .map(Record1::value1)
		        .collectList();
	}

}
