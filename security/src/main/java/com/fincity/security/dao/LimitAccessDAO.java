package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityAppLimitations.SECURITY_APP_LIMITATIONS;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppLimitationsRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LimitAccessDAO extends AbstractUpdatableDAO<SecurityAppLimitationsRecord, ULong, LimitAccess> {

    protected LimitAccessDAO() {
        super(LimitAccess.class, SECURITY_APP_LIMITATIONS, SECURITY_APP_LIMITATIONS.ID);
    }

    public Mono<Long> getByAppandClientId(ULong appId, ULong clientId, String objectName,
            ULong urlClientId) {

        Condition cond = DSL.and(SECURITY_APP_LIMITATIONS.APP_ID.eq(appId))
                .and(SECURITY_APP_LIMITATIONS.CLIENT_ID.in(clientId, urlClientId))
                .and(SECURITY_APP_LIMITATIONS.NAME.eq(objectName));

        return Flux.from(this.dslContext.selectFrom(SECURITY_APP_LIMITATIONS)
                .where(cond).orderBy(SECURITY_APP_LIMITATIONS.CLIENT_ID.desc()))
                .collectList().flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(list.get(0).getLimit()))
                .defaultIfEmpty(10L);
    }
}
