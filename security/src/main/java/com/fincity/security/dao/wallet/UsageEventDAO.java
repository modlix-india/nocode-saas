package com.fincity.security.dao.wallet;

import static com.fincity.security.jooq.tables.SecurityUsageEvent.SECURITY_USAGE_EVENT;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.wallet.UsageEvent;
import com.fincity.security.jooq.tables.records.SecurityUsageEventRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Durable consumption-log DAO. Inserts are append-only on the hot path; the
 * consolidation job scans an unconsolidated, closed time window and then purges
 * the rows it has debited (the ledger retains the priced aggregate for audit).
 */
@Component
public class UsageEventDAO extends AbstractDAO<SecurityUsageEventRecord, ULong, UsageEvent> {

    public UsageEventDAO() {
        super(UsageEvent.class, SECURITY_USAGE_EVENT, SECURITY_USAGE_EVENT.ID);
    }

    /** Unconsolidated rows in the closed window [, cutoff), oldest first, capped. */
    public Mono<List<UsageEvent>> findUnconsolidatedBefore(LocalDateTime cutoff, int limit) {
        return Flux.from(this.dslContext.selectFrom(SECURITY_USAGE_EVENT)
                .where(SECURITY_USAGE_EVENT.CONSOLIDATED.eq((byte) 0)
                        .and(SECURITY_USAGE_EVENT.CREATED_AT.lt(cutoff)))
                .orderBy(SECURITY_USAGE_EVENT.CREATED_AT.asc())
                .limit(limit))
                .map(r -> r.into(UsageEvent.class))
                .collectList();
    }

    /** Delete consumed rows once their aggregate has been debited to the ledger. */
    public Mono<Integer> purge(List<ULong> ids) {
        if (ids == null || ids.isEmpty())
            return Mono.just(0);
        return Mono.from(this.dslContext.deleteFrom(SECURITY_USAGE_EVENT)
                .where(SECURITY_USAGE_EVENT.ID.in(ids)));
    }
}
