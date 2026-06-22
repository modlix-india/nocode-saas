package com.fincity.security.service.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.wallet.UsageEventDAO;
import com.fincity.security.dto.wallet.UsageEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The 15-minute consolidation pass. Reads the durable consumption log for every
 * closed window up to the current 15-minute boundary, groups rows by
 * (consumer, exposing client, app, action), debits the resolved wallet once per
 * group (idempotent per window), then purges the consumed rows. The worker
 * service triggers this over Feign on a schedule; all the billing logic lives
 * here so the worker stays a dumb trigger.
 */
@Service
public class UsageConsolidationService {

    private static final int BATCH_LIMIT = 5000;

    private final UsageEventDAO usageEventDAO;
    private final WalletService walletService;

    public UsageConsolidationService(UsageEventDAO usageEventDAO, WalletService walletService) {
        this.usageEventDAO = usageEventDAO;
        this.walletService = walletService;
    }

    private record GroupKey(ULong clientId, ULong urlClientId, ULong appId, String actionKey) {
    }

    private static final class Agg {
        private BigDecimal quantity = BigDecimal.ZERO;
        private final List<ULong> ids = new ArrayList<>();
    }

    /** Consolidate all closed windows up to the current 15-minute boundary. */
    public Mono<Integer> consolidate() {
        LocalDateTime cutoff = currentWindowStart();
        long bucket = cutoff.toEpochSecond(ZoneOffset.UTC);

        return this.usageEventDAO.findUnconsolidatedBefore(cutoff, BATCH_LIMIT)
                .flatMap(rows -> {
                    if (rows.isEmpty())
                        return Mono.just(0);
                    return Flux.fromIterable(group(rows).entrySet())
                            .concatMap(e -> debitAndPurge(e.getKey(), e.getValue(), bucket))
                            .reduce(0, Integer::sum);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UsageConsolidationService.consolidate"));
    }

    private Mono<Integer> debitAndPurge(GroupKey k, Agg agg, long bucket) {
        // Idempotency is per (consumer, app, action, window); the wallet that
        // (clientId, appId) resolves to is deterministic, so this key is stable.
        String idem = "usage:" + k.clientId() + ":" + (k.appId() == null ? "0" : k.appId())
                + ":" + k.actionKey() + ":" + bucket;
        return this.walletService
                .consolidatedDebit(k.clientId(), k.urlClientId(), k.appId(), k.actionKey(), agg.quantity, idem)
                .then(this.usageEventDAO.purge(agg.ids))
                // A failed group is left for the next window; a replayed debit is a no-op.
                .onErrorResume(err -> Mono.just(0));
    }

    private Map<GroupKey, Agg> group(List<UsageEvent> rows) {
        Map<GroupKey, Agg> groups = new LinkedHashMap<>();
        for (UsageEvent r : rows) {
            Agg agg = groups.computeIfAbsent(
                    new GroupKey(r.getClientId(), r.getUrlClientId(), r.getAppId(), r.getActionKey()),
                    x -> new Agg());
            agg.quantity = agg.quantity.add(r.getQuantity() == null ? BigDecimal.ONE : r.getQuantity());
            agg.ids.add(r.getId());
        }
        return groups;
    }

    private LocalDateTime currentWindowStart() {
        LocalDateTime now = LocalDateTime.now();
        return now.truncatedTo(ChronoUnit.HOURS).plusMinutes((now.getMinute() / 15) * 15L);
    }
}
