package com.fincity.security.dao;

import static com.fincity.security.jooq.Tables.*;

import org.jooq.DatePart;
import org.jooq.Field;
import org.jooq.impl.DSL;
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

    /**
     * Redeem: read the token, delete it, and return it only if this call's delete removed it and
     * it is younger than {@code maxAgeMinutes}.
     *
     * <ul>
     * <li><b>Single use, even under a race.</b> Two concurrent redemptions both used to read the
     * row before either deleted it, and both got a session. Now only the call whose DELETE
     * affected the row proceeds.</li>
     * <li><b>Short-lived.</b> One-time tokens are SSO handoff strings redeemed within seconds of
     * minting, but nothing expired them: an unredeemed one stayed good forever. The age is
     * computed by the database ({@code CREATED_AT} is its own CURRENT_TIMESTAMP), so a JVM and
     * a database on different time zones cannot disagree about it. A stale token is still
     * deleted, it just does not sign anyone in.</li>
     * </ul>
     */
    public Mono<OneTimeToken> readOneTimeTokenAndDeleteBy(String token, int maxAgeMinutes) {

        Field<Boolean> fresh = DSL.field(SECURITY_ONE_TIME_TOKEN.CREATED_AT.ge(
                DSL.localDateTimeSub(DSL.currentLocalDateTime(), DSL.inline(maxAgeMinutes), DatePart.MINUTE)))
                .as("FRESH");

        return FlatMapUtil.flatMapMono(

                () -> Mono.from(this.dslContext.select(SECURITY_ONE_TIME_TOKEN.fields())
                        .select(fresh)
                        .from(SECURITY_ONE_TIME_TOKEN)
                        .where(SECURITY_ONE_TIME_TOKEN.TOKEN.eq(token))),

                row -> Mono.from(this.dslContext.deleteFrom(SECURITY_ONE_TIME_TOKEN)
                        .where(SECURITY_ONE_TIME_TOKEN.ID.eq(row.get(SECURITY_ONE_TIME_TOKEN.ID)))),

                (row, count) -> {
                    if (count == null || count != 1 || !Boolean.TRUE.equals(row.get(fresh)))
                        return Mono.<OneTimeToken>empty();

                    SecurityOneTimeTokenRecord rec = row.into(SECURITY_ONE_TIME_TOKEN);

                    return Mono.just(new OneTimeToken()
                            .setUserId(rec.getUserId())
                            .setRememberMe(ByteUtil.ONE.equals(rec.getRememberMe()))
                            .setToken(rec.getToken())
                            .setIpAddress(rec.getIpAddress())
                            .setUserAgent(rec.getUserAgent())
                            .setDeviceType(rec.getDeviceType())
                            .setOs(rec.getOs())
                            .setBrowser(rec.getBrowser())
                            .setAuthMode(rec.getAuthMode())
                            .setOriginAppCode(rec.getOriginAppCode())
                            .setTargetAppCode(rec.getTargetAppCode()));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OneTimeTokenDAO.readOneTimeTokenAndDeleteBy"));
    }
}
