package com.fincity.saas.message.dao.call;

import static com.fincity.saas.message.jooq.tables.MessageCallProviderApps.MESSAGE_CALL_PROVIDER_APPS;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.jooq.tables.records.MessageCallProviderAppsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CallProviderAppDAO extends BaseUpdatableDAO<MessageCallProviderAppsRecord, CallProviderApp> {

    public CallProviderAppDAO() {
        super(CallProviderApp.class, MESSAGE_CALL_PROVIDER_APPS, MESSAGE_CALL_PROVIDER_APPS.ID);
    }

    /**
     * The integration app registered for one tenant.
     *
     * <p>Not scoped by connection. One app serves every agent belonging to the tenant — agents are
     * rows in its user mapping, not a reason to register another app — and the app's name has never
     * carried a connection component. Keying the lookup on the connection made a tenant's second
     * CALL connection look like a tenant with no app, which is how a duplicate gets created at a
     * provider that accepts repeated names without complaint.
     */
    public Mono<CallProviderApp> findByClient(String appCode, String clientCode, String provider) {

        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_CALL_PROVIDER_APPS)
                        .where(MESSAGE_CALL_PROVIDER_APPS.APP_CODE.eq(appCode))
                        .and(MESSAGE_CALL_PROVIDER_APPS.CLIENT_CODE.eq(clientCode))
                        .and(MESSAGE_CALL_PROVIDER_APPS.PROVIDER.eq(provider))
                        .and(MESSAGE_CALL_PROVIDER_APPS.IS_ACTIVE.eq(Boolean.TRUE)))
                .map(rec -> rec.into(CallProviderApp.class));
    }

    /**
     * Records the callback URL after the row already exists.
     *
     * <p>Separate from creation on purpose: the row is written the instant the provider returns the
     * app secret, before anything else is attempted, because that secret cannot be read back. The
     * callback URL is registered afterwards and folded in here.
     */
    public Mono<Integer> updateCallbackUrl(ULong id, String callbackUrl) {

        return Mono.from(this.dslContext
                .update(MESSAGE_CALL_PROVIDER_APPS)
                .set(MESSAGE_CALL_PROVIDER_APPS.CALLBACK_URL, callbackUrl)
                .where(MESSAGE_CALL_PROVIDER_APPS.ID.eq(id)));
    }

    /**
     * Removes the registration outright, once the provider-side app is gone.
     *
     * <p>Scoped to the tenant, matching {@link #findByClient}. Purging by connection would leave the
     * row behind whenever teardown ran from a different connection than the one that created it, and
     * a surviving row for a deleted app is worse than no row: it is the one thing that stops the
     * next initialize from registering a replacement.
     */
    public Mono<Integer> purgeByClient(String appCode, String clientCode, String provider) {

        return Mono.from(this.dslContext
                .deleteFrom(MESSAGE_CALL_PROVIDER_APPS)
                .where(MESSAGE_CALL_PROVIDER_APPS.APP_CODE.eq(appCode))
                .and(MESSAGE_CALL_PROVIDER_APPS.CLIENT_CODE.eq(clientCode))
                .and(MESSAGE_CALL_PROVIDER_APPS.PROVIDER.eq(provider)));
    }
}
