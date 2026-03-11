package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityUserToken.SECURITY_USER_TOKEN;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TokenDAO extends AbstractDAO<SecurityUserTokenRecord, ULong, TokenObject> {

    protected TokenDAO() {
        super(TokenObject.class, SECURITY_USER_TOKEN, SECURITY_USER_TOKEN.ID);
    }

    public Flux<String> getTokensOfId(ULong userId) {

        return Flux.from(this.dslContext.select(SECURITY_USER_TOKEN.TOKEN).from(SECURITY_USER_TOKEN)
                .where(SECURITY_USER_TOKEN.USER_ID.eq(userId))).map(Record1::value1);
    }

    public Mono<Integer> deleteAllTokens(ULong id) {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_USER_TOKEN).where(SECURITY_USER_TOKEN.USER_ID.eq(id)));
    }

    public Mono<Integer> updateLastUsedAt(ULong tokenId) {

        return Mono.from(this.dslContext.update(SECURITY_USER_TOKEN)
                .set(SECURITY_USER_TOKEN.LAST_USED_AT, LocalDateTime.now())
                .where(SECURITY_USER_TOKEN.ID.eq(tokenId)));
    }

    public Mono<Integer> deleteExpiredTokens() {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_USER_TOKEN)
                .where(SECURITY_USER_TOKEN.EXPIRES_AT.lt(LocalDateTime.now())));
    }

    public Mono<Integer> deleteUnusedTokens(int unusedDays) {

        LocalDateTime cutoff = LocalDateTime.now().minusDays(unusedDays);

        return Mono.from(this.dslContext.deleteFrom(SECURITY_USER_TOKEN)
                .where(SECURITY_USER_TOKEN.LAST_USED_AT.isNotNull()
                        .and(SECURITY_USER_TOKEN.LAST_USED_AT.lt(cutoff))));
    }
}