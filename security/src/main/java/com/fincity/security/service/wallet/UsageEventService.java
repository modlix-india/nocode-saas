package com.fincity.security.service.wallet;

import java.math.BigDecimal;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.wallet.UsageEventDAO;
import com.fincity.security.dto.wallet.UsageEvent;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Writes durable consumption rows. This is the counting half of metering: one
 * append-only row per chargeable action (raw dimensions only). Pricing,
 * wallet resolution and debiting all happen later in the 15-minute
 * consolidation job, so this stays a single dumb INSERT on the hot path.
 *
 * <p>The caller decides whether an action is billable (config present and
 * enforced); this service only records.
 */
@Service
public class UsageEventService {

    private final UsageEventDAO dao;

    public UsageEventService(UsageEventDAO dao) {
        this.dao = dao;
    }

    public Mono<UsageEvent> record(ULong clientId, ULong urlClientId, ULong appId, ULong userId,
            String actionKey, BigDecimal quantity) {
        return this.dao.create(new UsageEvent()
                .setClientId(clientId)
                .setUrlClientId(urlClientId)
                .setAppId(appId)
                .setUserId(userId)
                .setActionKey(actionKey)
                .setQuantity(quantity == null ? BigDecimal.ONE : quantity)
                .setConsolidated(false))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UsageEventService.record"));
    }
}
