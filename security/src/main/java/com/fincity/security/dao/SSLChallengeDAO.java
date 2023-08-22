package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecuritySslChallenge.SECURITY_SSL_CHALLENGE;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.SSLChallenge;
import com.fincity.security.jooq.tables.records.SecuritySslChallengeRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SSLChallengeDAO extends AbstractUpdatableDAO<SecuritySslChallengeRecord, ULong, SSLChallenge> {

	protected SSLChallengeDAO() {
		super(SSLChallenge.class, SECURITY_SSL_CHALLENGE, SECURITY_SSL_CHALLENGE.ID);
	}

	public Mono<Boolean> deleteAllForRequest(ULong reqId) {

		return Mono.from(this.dslContext.delete(SECURITY_SSL_CHALLENGE)
				.where(SECURITY_SSL_CHALLENGE.REQUEST_ID.eq(reqId)))
				.map(e -> true);
	}

	public Mono<List<SSLChallenge>> readChallengesByRequestId(ULong reqId) {

		return Flux.from(this.dslContext.selectFrom(SECURITY_SSL_CHALLENGE)
				.where(SECURITY_SSL_CHALLENGE.REQUEST_ID.eq(reqId)))
				.map(e -> e.into(this.pojoClass))
				.collectList();
	}

	public Mono<Boolean> updateStatus(ULong id, String chStatus, String chError) {

		return Mono.from(this.dslContext.update(SECURITY_SSL_CHALLENGE)
				.set(SECURITY_SSL_CHALLENGE.STATUS, chStatus)
				.set(SECURITY_SSL_CHALLENGE.FAILED_REASON, chError)
				.where(SECURITY_SSL_CHALLENGE.ID.eq(id)))
				.map(e -> true);
	}

	public Mono<String> getToken(String token) {
		return Mono.from(this.dslContext.select(SECURITY_SSL_CHALLENGE.AUTHORIZATION)
				.from(SECURITY_SSL_CHALLENGE)
				.where(SECURITY_SSL_CHALLENGE.TOKEN.eq(token)))
				.map(e -> e.get(SECURITY_SSL_CHALLENGE.AUTHORIZATION))
				.defaultIfEmpty("");
	}
}
