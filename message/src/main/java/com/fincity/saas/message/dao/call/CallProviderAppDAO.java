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
     * The integration app registered for one tenant. Not scoped by connection: one app serves the whole tenant,
     * and keying on connection made a second connection look app-less and register a duplicate at the provider.
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
     * Records the callback URL after the row exists. The row is written the instant the provider returns the app
     * secret, which cannot be read back, before the callback is registered.
     */
    public Mono<Integer> updateCallbackUrl(ULong id, String callbackUrl) {

        return Mono.from(this.dslContext
                .update(MESSAGE_CALL_PROVIDER_APPS)
                .set(MESSAGE_CALL_PROVIDER_APPS.CALLBACK_URL, callbackUrl)
                .where(MESSAGE_CALL_PROVIDER_APPS.ID.eq(id)));
    }

    /**
     * Removes the registration once the provider-side app is gone. Scoped to the tenant like {@link #findByClient};
     * a surviving row for a deleted app would stop the next initialize from registering a replacement.
     */
    public Mono<Integer> purgeByClient(String appCode, String clientCode, String provider) {

        return Mono.from(this.dslContext
                .deleteFrom(MESSAGE_CALL_PROVIDER_APPS)
                .where(MESSAGE_CALL_PROVIDER_APPS.APP_CODE.eq(appCode))
                .and(MESSAGE_CALL_PROVIDER_APPS.CLIENT_CODE.eq(clientCode))
                .and(MESSAGE_CALL_PROVIDER_APPS.PROVIDER.eq(provider)));
    }
}
