package com.fincity.saas.entity.processor.service;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.service.product.ProductTicketRuRuleService;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Who is entitled to see a deal, answered without asking them.
 *
 * <p>The inverse of the condition {@code TicketDAO.processorAccessCondition} puts on every read.
 * That condition is built around a caller and answers "which deals may this person see"; this
 * answers "which people may see this deal", which is the question anything pushing a notification
 * actually has.
 *
 * <h2>Why it is split across two services</h2>
 *
 * <p>The read rule has two independent halves and they are owned by different services.
 *
 * <p>The first half is reporting lines and client hierarchy: assignee-in-my-sub-org, created-by for
 * a business partner, and client-managed-by-mine. Every input to that is security's, so security
 * inverts it in {@code RecordAudienceService} and this asks once.
 *
 * <p>The second half is per-product read rules, which are entity-processor's own tables. A user can
 * be granted a whole product's deals by user, role, designation, department or profile, with no
 * reporting relationship at all. Those are unioned on here.
 *
 * <p>Splitting it this way means neither service holds a copy of the other's rule. What it does not
 * mean is that the two halves are independently verifiable: the only thing that makes this
 * defensible is {@code TicketAudienceServiceTest}, which asserts that a user is in the audience
 * exactly when the real read returns the row for them.
 *
 * <h2>Fails closed</h2>
 *
 * <p>Any failure resolving the audience yields an empty audience, so a broken lookup makes the
 * feature go quiet rather than go wide. This sits behind a stream that now carries a lead's name and
 * message text, and the cost of being too generous is disclosing a customer's conversation to
 * somebody who cannot open the deal.
 */
@Service
public class TicketAudienceService {

    private static final Logger logger = LoggerFactory.getLogger(TicketAudienceService.class);

    private final IFeignSecurityService securityService;
    private final ProductTicketRuRuleService productTicketRuRuleService;

    public TicketAudienceService(
            IFeignSecurityService securityService, ProductTicketRuRuleService productTicketRuRuleService) {
        this.securityService = securityService;
        this.productTicketRuRuleService = productTicketRuRuleService;
    }

    public Mono<List<BigInteger>> audienceFor(Ticket ticket) {

        if (ticket == null) return Mono.just(List.of());

        // Both identifiers, because only one of them is reliably there. CLIENT_CODE is populated on
        // every ticket; CLIENT_ID on fewer than one in five, and the id-only version of this call
        // resolved an empty audience for the rest. An empty audience publishes nothing at all, so
        // the symptom was a stream that connected, heartbeat, and never delivered a single event.
        if (ticket.getClientId() == null && StringUtil.safeIsBlank(ticket.getClientCode())) {
            logger.error("Deal {} identifies no client; telling nobody about it.", ticket.getId());
            return Mono.just(List.of());
        }

        java.util.Map<String, Object> request = new java.util.HashMap<>();
        if (ticket.getClientId() != null) request.put("clientId", ticket.getClientId().toBigInteger());
        if (!StringUtil.safeIsBlank(ticket.getClientCode())) request.put("clientCode", ticket.getClientCode());
        if (ticket.getAssignedUserId() != null)
            request.put("assignedUserId", ticket.getAssignedUserId().toBigInteger());
        if (ticket.getCreatedBy() != null) request.put("createdBy", ticket.getCreatedBy().toBigInteger());
        request.put("activeOnly", Boolean.TRUE);

        return this.securityService
                .getRecordAudience(request)
                .defaultIfEmpty(List.of())
                .flatMap(fromStructure -> this.productRuleUsers(ticket)
                        .map(fromRules -> {
                            Set<BigInteger> audience = new LinkedHashSet<>(fromStructure);
                            audience.addAll(fromRules);
                            return List.copyOf(audience);
                        }))
                .onErrorResume(e -> {
                    logger.error("Could not resolve the audience for deal {}; telling nobody.", ticket.getId(), e);
                    return Mono.just(List.of());
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketAudienceService.audienceFor"));
    }

    /**
     * Users granted this deal by a product read rule rather than by who they manage.
     *
     * <p>Empty when the tenant defines no rules, which is the common case, and the call is a single
     * indexed query either way.
     */
    private Mono<List<BigInteger>> productRuleUsers(Ticket ticket) {

        return this.productTicketRuRuleService
                .getReadAudience(ticket)
                .defaultIfEmpty(List.of())
                .onErrorResume(e -> {
                    logger.error("Could not resolve product-rule readers for deal {}", ticket.getId(), e);
                    return Mono.just(List.of());
                });
    }
}
