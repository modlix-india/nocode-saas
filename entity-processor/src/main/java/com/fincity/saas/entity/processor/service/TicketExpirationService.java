package com.fincity.saas.entity.processor.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.TicketExpirationDAO;
import com.fincity.saas.entity.processor.dto.ExpireTicketsResult;
import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.rule.ProductTicketExRuleService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketExpirationService {

    private final ProductTicketExRuleService productTicketExRuleService;
    private final TicketExpirationDAO ticketExpirationDAO;

    public TicketExpirationService(
            ProductTicketExRuleService productTicketExRuleService,
            TicketExpirationDAO ticketExpirationDAO) {
        this.productTicketExRuleService = productTicketExRuleService;
        this.ticketExpirationDAO = ticketExpirationDAO;
    }

    public Mono<ExpireTicketsResult> runExpiration(ProcessorAccess access) {
        LocalDateTime now = LocalDateTime.now();

        Set<ULong> globalExpiredIds = ConcurrentHashMap.newKeySet();

        return FlatMapUtil.flatMapMono(
                () -> productTicketExRuleService.getActiveRules(access),
                rules -> {
                    List<ProductTicketExRule> sorted = rules.stream()
                            .sorted(Comparator.comparing(
                                    r -> r.getProductTemplateId() == null))
                            .toList();

                    return Flux.fromIterable(sorted)
                            .concatMap(rule -> {
                                LocalDateTime cutoff = now.minusDays(rule.getExpiryDays());
                                return ticketExpirationDAO
                                        .expireTicketsForRuleFully(
                                                rule, cutoff, now, globalExpiredIds);
                            })
                            .reduce(0, Integer::sum)
                            .defaultIfEmpty(0)
                            .map(ExpireTicketsResult::new);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "TicketExpirationService.runExpiration"));
    }
}
