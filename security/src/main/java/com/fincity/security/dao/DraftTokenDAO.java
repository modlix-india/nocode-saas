package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityDraftToken.SECURITY_DRAFT_TOKEN;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.DraftToken;
import com.fincity.security.jooq.tables.records.SecurityDraftTokenRecord;

import reactor.core.publisher.Mono;

@Component
public class DraftTokenDAO extends AbstractDAO<SecurityDraftTokenRecord, ULong, DraftToken> {

    protected DraftTokenDAO() {
        super(DraftToken.class, SECURITY_DRAFT_TOKEN, SECURITY_DRAFT_TOKEN.ID);
    }

    /**
     * The row a hostname resolves to, expired or not.
     *
     * Expiry is deliberately not filtered here. The gateway caches what this
     * resolves to and re-checks the expiry itself on every request, so it needs the
     * timestamp back rather than an empty answer -- see ClientUrlService.resolveDraftToken.
     */
    public Mono<DraftToken> readByToken(String token) {

        return Mono.from(this.dslContext.select(SECURITY_DRAFT_TOKEN.fields()).from(SECURITY_DRAFT_TOKEN)
                .where(SECURITY_DRAFT_TOKEN.TOKEN.eq(token))
                .limit(1))
                .map(rec -> rec.into(DraftToken.class));
    }

    /**
     * The live grant this user already holds for this app, freshest first.
     *
     * Keyed on the same triple {@code resolveDraftToken} later checks a request
     * against -- user, app, minting client -- so a reused row grants exactly what a
     * freshly minted one would have, and nothing downstream can tell the two apart.
     *
     * Expiry IS filtered here, unlike {@link #readByToken(String)}. That one answers
     * "what does this hostname mean", which an expired row still answers; this one
     * answers "may this be handed out again", which an expired row never may. A row
     * this returns also cannot vanish under the caller: the only thing that deletes
     * rows is the cleanup job, and it only takes rows already past their expiry.
     *
     * The ORDER BY matters because the table has no unique key on this triple -- two
     * windows opened in the same instant can both miss this read and both insert. The
     * loser's row is then simply an extra grant that expires on its own, and every
     * later window converges on whichever row is freshest.
     */
    public Mono<DraftToken> readLiveOfUser(ULong userId, String appCode, ULong clientId, LocalDateTime now) {

        return Mono.from(this.dslContext.select(SECURITY_DRAFT_TOKEN.fields()).from(SECURITY_DRAFT_TOKEN)
                .where(SECURITY_DRAFT_TOKEN.USER_ID.eq(userId)
                        .and(SECURITY_DRAFT_TOKEN.APP_CODE.eq(appCode))
                        .and(SECURITY_DRAFT_TOKEN.CLIENT_ID.eq(clientId))
                        .and(SECURITY_DRAFT_TOKEN.EXPIRES_AT.gt(now)))
                .orderBy(SECURITY_DRAFT_TOKEN.EXPIRES_AT.desc())
                .limit(1))
                .map(rec -> rec.into(DraftToken.class));
    }

    /**
     * Push an existing token's expiry forward, without changing its value.
     *
     * The token and therefore the hostname must survive a whole editing session:
     * minting a replacement would change the iframe's origin and reload all three
     * canvases, losing scroll position and everything the previewed page holds in
     * its own store.
     *
     * Scoped to the minting user so one person's heartbeat cannot keep another
     * person's grant alive.
     */
    public Mono<Integer> extend(String token, ULong userId, LocalDateTime expiresAt) {

        return Mono.from(this.dslContext.update(SECURITY_DRAFT_TOKEN)
                .set(SECURITY_DRAFT_TOKEN.EXPIRES_AT, expiresAt)
                .where(SECURITY_DRAFT_TOKEN.TOKEN.eq(token)
                        .and(SECURITY_DRAFT_TOKEN.USER_ID.eq(userId))));
    }

    /** Housekeeping for the token cleanup job. */
    public Mono<Integer> deleteExpired() {

        return Mono.from(this.dslContext.deleteFrom(SECURITY_DRAFT_TOKEN)
                .where(SECURITY_DRAFT_TOKEN.EXPIRES_AT.lt(LocalDateTime.now(java.time.ZoneOffset.UTC))));
    }
}
