package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityAppOwnerLimitations.SECURITY_APP_OWNER_LIMITATIONS;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppOwnerLimitationsRecord;

import reactor.core.publisher.Mono;

public class LimitOwnerAccessDAO extends AbstractUpdatableDAO<SecurityAppOwnerLimitationsRecord, ULong, LimitAccess> {

	protected LimitOwnerAccessDAO() {
		super(LimitAccess.class, SECURITY_APP_OWNER_LIMITATIONS, SECURITY_APP_OWNER_LIMITATIONS.ID);
	}
	
	public Mono<LimitAccess> getByAppandClientId(ULong appId, ULong clientId, String objectName) {

		Condition cond = DSL.and(SECURITY_APP_OWNER_LIMITATIONS.APP_ID.eq(appId))
		        .and(SECURITY_APP_OWNER_LIMITATIONS.CLIENT_ID.eq(clientId))
		        .and(SECURITY_APP_OWNER_LIMITATIONS.NAME.eq(objectName));

		return Mono.from(this.dslContext.select(SECURITY_APP_OWNER_LIMITATIONS.fields())
		        .from(SECURITY_APP_OWNER_LIMITATIONS)
		        .where(cond)
		        .limit(1))
		        .map(e -> e.into(LimitAccess.class));
	}
}
