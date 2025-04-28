package com.fincity.security.service.appregistration;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
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

    public Mono<AppRegistrationIntegrationToken> verifyIntegrationState(String state) {

        return this.dao.findByState(state)
                .switchIfEmpty(Mono.error(new Exception("Invalid state")));
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
