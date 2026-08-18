package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.response.message.WhatsappSessionHealth;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Read side of the WhatsApp sending configuration: which numbers are linked, and how they are doing.
 *
 * <p>Templates and business numbers used to be what this served. Both went with the Cloud API:
 * there is no approval to check and no WABA to list. What replaced them is a smaller and more useful
 * question, which is whether the number a person is about to send from is healthy enough to send.
 *
 * <p>This exists so the UI has one service to talk to. The message service owns the session rows and
 * still serves them on its own routes, but those are denied at the edge along with the rest of
 * {@code /api/message/**}. Reading them through here keeps the deal profile working and leaves the
 * message service reachable only by services.
 *
 * <p>Not gated on {@code ROLE_Owner}, deliberately. A salesperson opening a deal has to see whether
 * the number is connected before they can send at all, and that is deal work rather than settings
 * administration. Gating it on the administrative authority would blank the composer for exactly the
 * people who use it. Linking and unlinking a number are a different matter and stay gated.
 */
@Service
public class WhatsappSendOptionsService implements IProcessorAccessService {

    /** The session's own identifier in the listing, which products point at. */
    private static final String KEY_SESSION_CODE = "code";

    private final IFeignMessageService feignMessageService;
    private final ProcessorMessageResourceService msgService;
    private final IFeignSecurityService securityService;
    private final WhatsappSessionService sessionService;
    private final ProductService productService;

    public WhatsappSendOptionsService(
            IFeignMessageService feignMessageService,
            ProcessorMessageResourceService msgService,
            IFeignSecurityService securityService,
            WhatsappSessionService sessionService,
            ProductService productService) {
        this.feignMessageService = feignMessageService;
        this.msgService = msgService;
        this.securityService = securityService;
        this.sessionService = sessionService;
        this.productService = productService;
    }

    @Override
    public ProcessorMessageResourceService getMsgService() {
        return this.msgService;
    }

    @Override
    public IFeignSecurityService getSecurityService() {
        return this.securityService;
    }

    /**
     * Every linked number the tenant has, which is what the integration page lists.
     *
     * <p>Each row is decorated with the products that send from it. Done here rather than on the
     * page because the page cannot do it: the mapping is held per product, the list is per number,
     * and the Modlix expression engine has no way to group one by the other. Without this the screen
     * could show numbers or products but never which belongs to which.
     *
     * <p>One extra query for the whole list, not one per number.
     */
    public Mono<List<Map<String, Object>>> readSessions() {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService
                                .listWhatsappSessions(access.getAppCode(), access.getClientCode())
                                .defaultIfEmpty(List.of()),
                        (access, sessions) -> this.productService
                                .getAllProducts(access, null)
                                .defaultIfEmpty(List.of()),
                        (access, sessions, products) -> Mono.just(this.withProducts(sessions, products)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readSessions"));
    }

    private List<Map<String, Object>> withProducts(List<Map<String, Object>> sessions, List<Product> products) {

        Map<String, List<Product>> byCode = products.stream()
                .filter(product -> !StringUtil.safeIsBlank(product.getWhatsappSessionCode()))
                .collect(Collectors.groupingBy(Product::getWhatsappSessionCode));

        return sessions.stream()
                .map(session -> {
                    // Copied rather than mutated: what feign decoded is not ours to write into, and a
                    // shared decoder cache would carry the additions to the next caller.
                    Map<String, Object> row = new LinkedHashMap<>(session);
                    List<Product> mapped = byCode.getOrDefault(String.valueOf(session.get(KEY_SESSION_CODE)), List.of());

                    row.put("productIds", mapped.stream().map(p -> p.getId().toBigInteger()).toList());
                    row.put("productNames", mapped.stream().map(Product::getName).collect(Collectors.joining(", ")));
                    return row;
                })
                .toList();
    }

    /**
     * The session a product sends from.
     *
     * <p>Answers an empty object rather than an empty body when nothing is linked. That distinction
     * matters on the wire: an empty body arrives at the UI as an empty string, and a binding
     * expecting an object then indexes into it and fails on every field.
     */
    public Mono<Map<String, Object>> readSessionForProduct(BigInteger productId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService
                                .getWhatsappSessionByProduct(access.getAppCode(), access.getClientCode(), productId)
                                .defaultIfEmpty(Map.of()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readSessionForProduct"));
    }

    /** Live state for one session, read from the instance holding it rather than from our cached row. */
    public Mono<Map<String, Object>> readSession(String sessionId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.getWhatsappSession(
                                access.getAppCode(), access.getClientCode(), sessionId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readSession"));
    }

    /** The current pairing code, polled every couple of seconds while a number is being linked. */
    public Mono<Map<String, Object>> readQr(String sessionId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.getWhatsappSessionQr(
                                access.getAppCode(), access.getClientCode(), sessionId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readQr"));
    }

    /**
     * How a number is doing against every limit that keeps it alive.
     *
     * <p>One computation feeding two surfaces: the standing panel on the integration page, and the
     * override panel that appears when somebody is about to send during a hold. They must not be
     * able to disagree, because the entire purpose of the override panel is that a person makes a
     * decision on the strength of these numbers.
     *
     * <p>Delegated rather than computed here, so the session row is read by one set of field names.
     * This method used to pluck {@code phone} and {@code state} off the row, which are not what the
     * row calls them, and the panel showed a blank number in a blank state without erroring.
     *
     * <p>Opt-out is passed as false because it is a property of a deal and there is no deal here.
     * The per-deal reader supplies the real value; the holds this one can report are the ones that
     * belong to the number itself, which is exactly what the standing panel is for.
     *
     * @param ticketIds the deal being looked at, or empty for the standing tenant-level view. The
     *     24-hour figures are per-deal and are simply absent without it.
     */
    public Mono<WhatsappSessionHealth> readHealth(String sessionId, List<ULong> ticketIds) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.readSession(sessionId),
                        (access, session) -> this.sessionService.healthWithDecision(
                                access.getAppCode(), access.getClientCode(), session, ticketIds, false, null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readHealth"));
    }

    /**
     * Starts linking a number, returning as soon as an instance is pairing.
     *
     * <p>Gated, unlike everything above it. Reading a number's state is deal work that a salesperson
     * needs before they can send at all; linking one commits the tenant's real business number to a
     * device we control and consumes a slot on a country's instance, which is administration.
     *
     * <p>Returns before the customer has scanned, because that can take minutes and may never
     * happen. The pairing code is polled from {@link #readQr} afterwards.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<Map<String, Object>> createSession(Map<String, Object> request) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.createWhatsappSession(
                                access.getAppCode(), access.getClientCode(), request))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.createSession"));
    }

    /**
     * Unlinks a number and forgets its device store.
     *
     * <p>Not reversible by undo: relinking means the customer scans a fresh QR code from their
     * handset. The conversation history stays, because it lives here rather than on the bridge.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<Boolean> unlinkSession(String sessionId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService
                                .unlinkWhatsappSession(access.getAppCode(), access.getClientCode(), sessionId)
                                .thenReturn(Boolean.TRUE))
                .defaultIfEmpty(Boolean.TRUE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.unlinkSession"));
    }

    /**
     * Makes one number the tenant's fallback, for every product that names none.
     *
     * <p>Gated where assigning a number to a product is not, and the difference is deliberate.
     * Pointing one product at one number affects that product, and whoever may edit the product may
     * decide it. The default is what happens to everything nobody has configured, including products
     * created later by people who never saw this screen, so it is an account-level decision and sits
     * with linking and unlinking.
     *
     * <p>False when the code names nothing placeable, rather than an error: the caller's list can be
     * a few seconds stale and a number retired in that window is not a fault worth a 500.
     */
    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public Mono<Boolean> markDefaultSession(String sessionId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.markWhatsappSessionDefault(
                                access.getAppCode(), access.getClientCode(), sessionId))
                .defaultIfEmpty(Boolean.FALSE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.markDefaultSession"));
    }
}
