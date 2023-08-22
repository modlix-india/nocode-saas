package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecuritySslRequest.SECURITY_SSL_REQUEST;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.SSLRequest;
import com.fincity.security.jooq.tables.SecuritySslRequest;
import com.fincity.security.jooq.tables.records.SecuritySslRequestRecord;

import reactor.core.publisher.Mono;

@Component
public class SSLRequestDAO extends AbstractUpdatableDAO<SecuritySslRequestRecord, ULong, SSLRequest> {

	protected SSLRequestDAO() {
		super(SSLRequest.class, SECURITY_SSL_REQUEST, SECURITY_SSL_REQUEST.ID);
	}

	public Mono<Boolean> checkIfRequestExistOnURL(ULong urlId) {

		return Mono.from(this.dslContext.selectCount()
				.from(SecuritySslRequest.SECURITY_SSL_REQUEST)
				.where(SecuritySslRequest.SECURITY_SSL_REQUEST.URL_ID.eq(urlId)))
				.map(Record1::value1)
				.map(e -> e != 0);
	}

	public Mono<SSLRequest> readByURLId(ULong urlId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_SSL_REQUEST)
				.where(SECURITY_SSL_REQUEST.URL_ID.eq(urlId)))
				.map(e -> e.into(this.pojoClass));
	}

	public Mono<Boolean> deleteByURLId(ULong urlId) {
		return Mono.from(this.dslContext.deleteFrom(SECURITY_SSL_REQUEST)
				.where(SECURITY_SSL_REQUEST.URL_ID.eq(urlId)))
				.map(e -> e == 1);
	}
}
