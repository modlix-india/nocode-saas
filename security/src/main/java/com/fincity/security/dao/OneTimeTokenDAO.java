package com.fincity.security.dao;

import static com.fincity.security.jooq.Tables.*;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.jooq.tables.records.SecurityOneTimeTokenRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class OneTimeTokenDAO extends AbstractDAO<SecurityOneTimeTokenRecord, ULong, OneTimeToken> {

    protected OneTimeTokenDAO() {
        super(OneTimeToken.class, SECURITY_ONE_TIME_TOKEN, SECURITY_ONE_TIME_TOKEN.ID);
    }

    public Mono<OneTimeToken> readOneTimeTokenAndDeleteBy(String token) {

        return FlatMapUtil.flatMapMono(

                () -> Mono.from(this.dslContext.selectFrom(SECURITY_ONE_TIME_TOKEN)
                        .where(SECURITY_ONE_TIME_TOKEN.TOKEN.eq(token))),

                rec -> Mono.from(this.dslContext.deleteFrom(SECURITY_ONE_TIME_TOKEN)
                        .where(SECURITY_ONE_TIME_TOKEN.ID.eq(rec.getId()))),

                (rec, count) -> Mono.just(new OneTimeToken()
                        .setUserId(rec.getUserId())
                        .setRememberMe(ByteUtil.ONE.equals(rec.getRememberMe()))
                        .setToken(rec.getToken())
                        .setIpAddress(rec.getIpAddress())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OneTimeTokenDAO.readOneTimeTokenAndDeleteBy"));
    }
}
