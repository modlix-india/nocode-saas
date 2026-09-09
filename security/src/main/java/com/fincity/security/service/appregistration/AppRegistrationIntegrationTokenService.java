package com.fincity.security.service.appregistration;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AppRegistrationIntegrationTokenService extends
        AbstractJOOQUpdatableDataService<SecurityAppRegIntegrationTokensRecord, ULong, AppRegistrationIntegrationToken, AppRegistrationIntegrationTokenDao> {

    private static final String AUTH_CODE = "authCode";
    private static final String TOKEN = "token";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String TOKEN_METADATA = "tokenMetadata";
    private static final String USER_METADATA = "userMetadata";
    private static final String USERNAME = "username";

    /**
     * How long a social-login state stays spendable after the provider verified the identity.
     * <p>
     * Measured from {@code UPDATED_AT}, which is when the OAuth callback wrote the verified
     * username onto the row, NOT from {@code CREATED_AT}, which is when the evoke call minted
     * it. The gap between the two is the account chooser, the password prompt and any 2FA: it
     * is the user's time, not ours, and bounding it is what made a first-time signup fail with
     * "session expired" for pausing on a consent screen. What this window really has to cover
     * is one browser redirect from the callback to the app, so it is generous at ten minutes.
     */
    private static final int STATE_VALID_FOR_MINUTES = 10;

    public Mono<AppRegistrationIntegrationToken> verifyIntegrationState(String state) {

        return this.dao.findByState(state)
                .switchIfEmpty(Mono.error(new Exception("Invalid state")));
    }

    public static boolean isStateExpired(AppRegistrationIntegrationToken token) {

        LocalDateTime verifiedAt = token.getUpdatedAt() != null ? token.getUpdatedAt() : token.getCreatedAt();
        if (verifiedAt == null)
            return true;

        // The row's timestamps are MySQL's own CURRENT_TIMESTAMP on a UTC connection, so the
        // comparison has to be made in UTC. A bare now() reads the JVM's zone, and on a
        // JVM east of UTC that alone expires every state the instant it is minted.
        return verifiedAt.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(STATE_VALID_FOR_MINUTES));
    }

    /**
     * Spend the state, so the same one cannot buy a second session.
     * <p>
     * Call this only once a session actually exists. The social return leg signs in first and
     * registers only if the app says it does not know the user, so the state has to outlive
     * that refused sign-in; and a failed registration must leave it spendable too, or a
     * transient error costs the user their whole trip through the provider.
     * <p>
     * Clearing {@code STATE} rather than deleting the row keeps the provider tokens and the
     * audit trail, and the column's UNIQUE key makes the update its own lock: exactly one
     * concurrent caller can see a row count of 1, so two tabs racing the same arrival cannot
     * both win. Not routed through {@code update()} on purpose, since
     * {@link #updatableEntity(AppRegistrationIntegrationToken)} copies a fixed field list that
     * does not include the state.
     */
    public Mono<Boolean> consumeState(String state) {
        return this.dao.consumeState(state);
    }

    @Override
    public Mono<AppRegistrationIntegrationToken> updatableEntity(AppRegistrationIntegrationToken entity) {
        return this.read(entity.getId())
                .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication().map(ca -> {
                    existing.setAuthCode(entity.getAuthCode());
                    existing.setToken(entity.getToken());
                    existing.setRefreshToken(entity.getRefreshToken());
                    existing.setExpiresAt(entity.getExpiresAt());
                    existing.setUsername(entity.getUsername());
                    existing.setTokenMetadata(entity.getTokenMetadata());
                    existing.setUserMetadata(entity.getUserMetadata());
                    return existing;
                }));
    }

}
