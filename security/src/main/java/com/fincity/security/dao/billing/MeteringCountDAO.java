package com.fincity.security.dao.billing;

import static com.fincity.security.jooq.Tables.SECURITY_APP;
import static com.fincity.security.jooq.Tables.SECURITY_PROFILE;
import static com.fincity.security.jooq.Tables.SECURITY_PROFILE_USER;
import static com.fincity.security.jooq.Tables.SECURITY_USER;

import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

import reactor.core.publisher.Mono;

/**
 * Read-only counts for security-owned metered actions (app/site rent, per-user).
 * Bound to SECURITY_APP only to obtain the reactive DSL context.
 */
@Component
public class MeteringCountDAO extends AbstractDAO<SecurityAppRecord, ULong, App> {

    protected MeteringCountDAO() {
        super(App.class, SECURITY_APP, SECURITY_APP.ID);
    }

    public Mono<Integer> countAppsOwnedBy(ULong clientId) {
        return countAppsByType(clientId, SecurityAppAppType.APP);
    }

    public Mono<Integer> countSitesOwnedBy(ULong clientId) {
        return countAppsByType(clientId, SecurityAppAppType.SITE);
    }

    private Mono<Integer> countAppsByType(ULong clientId, SecurityAppAppType type) {
        return Mono.from(this.dslContext.select(DSL.count())
                .from(SECURITY_APP)
                .where(SECURITY_APP.CLIENT_ID.eq(clientId))
                .and(SECURITY_APP.APP_TYPE.eq(type)))
                .map(Record1::value1);
    }

    /** Distinct users of a client that have at least one profile in the app. */
    public Mono<Integer> countUsersWithProfileInApp(ULong clientId, ULong appId) {
        return Mono.from(this.dslContext.select(DSL.countDistinct(SECURITY_USER.ID))
                .from(SECURITY_USER)
                .join(SECURITY_PROFILE_USER).on(SECURITY_PROFILE_USER.USER_ID.eq(SECURITY_USER.ID))
                .join(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                .where(SECURITY_USER.CLIENT_ID.eq(clientId))
                .and(SECURITY_PROFILE.APP_ID.eq(appId)))
                .map(Record1::value1);
    }
}
