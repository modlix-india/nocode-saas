package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.tables.NotificationUserPreferences.NOTIFICATION_USER_PREFERENCES;

import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferencesRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UserPreferenceDao extends AbstractCodeDao<NotificationUserPreferencesRecord, ULong, UserPreference> {

    protected UserPreferenceDao() {
        super(
                UserPreference.class,
                NOTIFICATION_USER_PREFERENCES,
                NOTIFICATION_USER_PREFERENCES.ID,
                NOTIFICATION_USER_PREFERENCES.CODE);
    }

    public Mono<UserPreference> getUserPreference(ULong appId, ULong userId) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(NOTIFICATION_USER_PREFERENCES.APP_ID.eq(appId))
                        .and(NOTIFICATION_USER_PREFERENCES.USER_ID.eq(userId)))
                .map(result -> result.into(this.pojoClass));
    }
}
