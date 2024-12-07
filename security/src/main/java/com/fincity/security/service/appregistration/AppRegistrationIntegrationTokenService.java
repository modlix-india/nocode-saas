package com.fincity.security.service.appregistration;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
import java.util.HashMap;
import java.util.Map;
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

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		Map<String, Object> newFields = new HashMap<>();

		if (fields.containsKey(AUTH_CODE))
			newFields.put(AUTH_CODE, fields.get(AUTH_CODE));
		if (fields.containsKey(TOKEN))
			newFields.put(TOKEN, fields.get(TOKEN));
		if (fields.containsKey(REFRESH_TOKEN))
			newFields.put(REFRESH_TOKEN, fields.get(REFRESH_TOKEN));
		if (fields.containsKey(EXPIRES_AT))
			newFields.put(EXPIRES_AT, fields.get(EXPIRES_AT));
		if (fields.containsKey(TOKEN_METADATA))
			newFields.put(TOKEN_METADATA, fields.get(TOKEN_METADATA));
		if (fields.containsKey(USER_METADATA))
			newFields.put(USER_METADATA, fields.get(USER_METADATA));
		if (fields.containsKey(USERNAME))
			newFields.put(USERNAME, fields.get(USERNAME));

		return Mono.just(newFields);
	}

}
