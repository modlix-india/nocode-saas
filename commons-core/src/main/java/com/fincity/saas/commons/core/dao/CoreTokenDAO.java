package com.fincity.saas.commons.core.dao;

import static com.fincity.saas.commons.core.jooq.tables.CoreTokens.CORE_TOKENS;

import com.fincity.saas.commons.core.dto.CoreToken;
import com.fincity.saas.commons.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.commons.core.jooq.tables.records.CoreTokensRecord;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class CoreTokenDAO extends AbstractUpdatableDAO<CoreTokensRecord, ULong, CoreToken> {

    protected CoreTokenDAO() {
        super(CoreToken.class, CORE_TOKENS, CORE_TOKENS.ID);
    }

    public Mono<Tuple2<String, LocalDateTime>> getActiveAccessTokenTuple(
            String clientCode, String appCode, String connectionName) {
        return Mono.from(this.dslContext
                        .selectFrom(CORE_TOKENS)
                        .where(CORE_TOKENS.CLIENT_CODE.eq(clientCode))
                        .and(CORE_TOKENS.APP_CODE.eq(appCode))
                        .and(CORE_TOKENS.CONNECTION_NAME.eq(connectionName))
                        .and(CORE_TOKENS.IS_REVOKED.eq((byte) 0))
                        .and(CORE_TOKENS.EXPIRES_AT.greaterThan(LocalDateTime.now())))
                .map(result -> Tuples.of(result.get(CORE_TOKENS.TOKEN), result.get(CORE_TOKENS.EXPIRES_AT)));
    }

    public Mono<CoreToken> getActiveToken(String clientCode, String appCode, String connectionName, CoreTokensTokenType tokenType) {
        return Mono.from(this.dslContext
                        .selectFrom(CORE_TOKENS)
                        .where(CORE_TOKENS.CLIENT_CODE.eq(clientCode))
                        .and(CORE_TOKENS.APP_CODE.eq(appCode))
                        .and(CORE_TOKENS.CONNECTION_NAME.eq(connectionName))
                        .and(CORE_TOKENS.TOKEN_TYPE.eq(tokenType))
                        .and(CORE_TOKENS.TOKEN.isNotNull())
                        .and(CORE_TOKENS.IS_REVOKED.eq((byte) 0))
                        .and(DSL.when(CORE_TOKENS.EXPIRES_AT.isNull(), CORE_TOKENS.IS_LIFETIME_TOKEN.eq((byte) 1))
                                .otherwise(CORE_TOKENS.EXPIRES_AT.greaterThan(LocalDateTime.now())))
                        .limit(1))
                .map(e -> e.into(CoreToken.class));
    }

    public Mono<CoreToken> getCoreTokenByState(String state) {
        return Mono.from(this.dslContext.selectFrom(CORE_TOKENS).where(CORE_TOKENS.STATE.eq(state)))
                .map(e -> e.into(CoreToken.class));
    }

    public Mono<Boolean> revokeToken(
            String clientCode, String appCode, String connectionName, CoreTokensTokenType tokenType) {

        List<Condition> conditions = new ArrayList<>();

        if (tokenType != null) conditions.add(CORE_TOKENS.TOKEN_TYPE.eq(tokenType));

        conditions.add(CORE_TOKENS.CLIENT_CODE.eq(clientCode));
        conditions.add(CORE_TOKENS.APP_CODE.eq(appCode));
        conditions.add(CORE_TOKENS.CONNECTION_NAME.eq(connectionName));
        conditions.add(CORE_TOKENS.IS_REVOKED.eq((byte) 0));

        return Mono.from(this.dslContext
                        .update(CORE_TOKENS)
                        .set(CORE_TOKENS.IS_REVOKED, (byte) 1)
                        .where(DSL.and(conditions)))
                .map(e -> e > 0);
    }
}
