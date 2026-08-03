package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.util.Map;
import java.util.Optional;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Read side of WhatsApp conversations, gated on deal access.
 *
 * <p>This exists because the message service cannot answer "may this user see this conversation?".
 * It knows about numbers and messages, not deals, reporting lines or product rules. Those live
 * here, in {@code TicketDAO}'s access condition, so this service is the only public way into a
 * WhatsApp thread and the message service's own listing endpoints are closed.
 */
@Service
public class TicketWhatsappConversationService {

    private final TicketService ticketService;
    private final ProductService productService;
    private final IFeignMessageService feignMessageService;

    public TicketWhatsappConversationService(
            TicketService ticketService, ProductService productService, IFeignMessageService feignMessageService) {
        this.ticketService = ticketService;
        this.productService = productService;
        this.feignMessageService = feignMessageService;
    }

    /**
     * A deal's WhatsApp thread.
     *
     * <p>The gate is {@code readByIdentity(access, ...)}, which runs the same condition every other
     * deal read uses: assigned-user within the caller's reporting tree, business-partner client
     * scoping, and per-product read rules. A ticket the caller cannot see fails there, before the
     * message service is ever called.
     *
     * <p>Deliberately no separate WhatsApp authority. This app scopes roles to entities (Deal,
     * Lead, Product, Partner) and not to the tabs within a deal: notes, tasks, activities and call
     * logs all ride on deal access alone. A conversation is deal sub-data, so it does the same. A
     * second gate here would only add a way for the feature to be silently dead wherever the role
     * was never provisioned.
     */
    public Mono<Map<String, Object>> readTicketThread(Identity ticketId, int page, int size) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> this.ticketService.readByIdentity(access, ticketId),
                        (access, ticket) -> this.feignMessageService.getTicketWhatsappMessages(
                                access.getAppCode(),
                                access.getClientCode(),
                                ticket.getId().toBigInteger(),
                                page,
                                size))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readTicketThread"));
    }

    /**
     * The inbox: one row per customer number, over the deals the caller can see.
     *
     * <p>No access logic of its own. The list is the deal list, so the gate is whatever {@code
     * TicketDAO.processorAccessCondition} says, which is the same rule the Deals screen runs on.
     * That is the point of building the inbox this way: there is no second definition of visibility
     * that could drift from the first.
     *
     * @param productId optional, narrows to one product's deals
     * @param search optional, matches deal name or customer number
     */
    public Mono<Page<WhatsappConversationResponse>> readConversations(
            Identity productId, String search, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this.ticketService::hasAccess,
                        access -> productId == null || productId.isNull()
                                ? Mono.just(Optional.<ULong>empty())
                                : this.productService
                                        .readByIdentity(access, productId)
                                        .map(product -> Optional.of(product.getId())),
                        (access, resolvedProductId) -> this.ticketService.readConversations(
                                access, resolvedProductId.orElse(null), search, pageable))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketWhatsappConversationService.readConversations"));
    }
}
