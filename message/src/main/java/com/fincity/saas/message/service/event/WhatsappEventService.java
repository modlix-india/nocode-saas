package com.fincity.saas.message.service.event;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.ClientRelationUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.model.event.MessageServerEvent;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.context.Context;

/**
 * "This conversation changed" for pages showing WhatsApp threads that core owns.
 *
 * <p>A nudge, not the message. The page re-reads the thread through its own server function, which
 * is where deal visibility is enforced; this stream only says which number and which customer to
 * re-read, so it never has to know who may see what.
 *
 * <p>One stream per app and client rather than per user, unlike calls. A call rings one person; an
 * inbound message belongs to whichever deal it matches, which only core can work out, so every
 * open page of the tenant hears it and the ones not showing that customer ignore it.
 *
 * <p>Held in memory on the instance that received the event. A page connected to another instance
 * of this service does not hear it, exactly as with the call streams.
 */
@Service
public class WhatsappEventService extends AbstractServerSentEventService {

    public static final String EVENT_TYPE_CONVERSATION_CHANGED = "WHATSAPP_CONVERSATION_CHANGED";

    private final IFeignSecurityService securityService;

    public WhatsappEventService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    /** The client level type of a partner (a client managed by the tenant, working its deals). */
    static final String LEVEL_TYPE_CUSTOMER = "CUSTOMER";

    /** Customer phone fields a partner's page never receives on this stream. */
    static final List<String> PHONE_FIELDS = List.of("customerWaId", "customerPhoneNumber");

    /**
     * The tenant's stream, for a caller entitled to it.
     *
     * <p>The caller's client must have access to the app (own it or have an app access row), unless
     * it is the system client; being the request's app is not enough on its own. The client must be
     * the caller's own, or (the partner case, where a partner's users work the tenant's deals) the
     * caller's client's DIRECT managing client, which must not be SYSTEM and must have write access
     * to the app: the same rule the message functions use,
     * {@link ClientRelationUtil#isSelfOrDirectManagingClientCode}. A client the caller manages, a
     * grandparent and SYSTEM are refused rather than handed an empty stream, because the key here is
     * the app and client and not the caller, so an unchecked request would be a way in.
     *
     * <p>A partner (a CUSTOMER-level client, or any caller watching a client other than its own)
     * gets each event without the customer phone fields; it re-reads the thread through its own
     * server function, which decides what it may see.
     */
    public Flux<MessageServerEvent> getConversationStream(String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.mayWatch(ca, appCode, clientCode),
                        (ca, allowed) -> Boolean.TRUE.equals(allowed) ? Mono.just(isPartner(ca, clientCode))
                                : Mono.<Boolean>empty())
                .switchIfEmpty(Mono.error(new GenericException(
                        HttpStatus.FORBIDDEN, "Not allowed to watch WhatsApp conversations of " + clientCode)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappEventService.getConversationStream"))
                .flatMapMany(partner -> {
                    Flux<MessageServerEvent> events = this.getEventStream(appCode, clientCode, null);
                    return Boolean.TRUE.equals(partner) ? events.map(WhatsappEventService::withoutPhones) : events;
                });
    }

    static boolean isPartner(ContextAuthentication ca, String clientCode) {
        return LEVEL_TYPE_CUSTOMER.equals(ca.getClientLevelType()) || !clientCode.equals(ca.getClientCode());
    }

    /** A copy without the phone fields: the sink hands every subscriber the same instance. */
    static MessageServerEvent withoutPhones(MessageServerEvent event) {
        Map<String, Object> data = event.getData() == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(event.getData());
        PHONE_FIELDS.forEach(data::remove);
        return new MessageServerEvent()
                .setId(event.getId())
                .setEventType(event.getEventType())
                .setTimestamp(event.getTimestamp())
                .setAppCode(event.getAppCode())
                .setClientCode(event.getClientCode())
                .setUserId(event.getUserId())
                .setData(data);
    }

    private Mono<Boolean> mayWatch(ContextAuthentication ca, String appCode, String clientCode) {
        if (clientCode == null || clientCode.isBlank() || appCode == null || appCode.isBlank())
            return Mono.just(Boolean.FALSE);

        String callerClientCode = ca.getClientCode();
        if (callerClientCode == null || callerClientCode.isBlank()) return Mono.just(Boolean.FALSE);

        Mono<Boolean> appAllowed = ca.isSystemClient()
                ? Mono.just(Boolean.TRUE)
                : this.securityService.hasReadAccess(appCode, callerClientCode).defaultIfEmpty(Boolean.FALSE);

        return appAllowed.flatMap(app -> Boolean.TRUE.equals(app)
                ? ClientRelationUtil.isSelfOrDirectManagingClientCode(
                        this.securityService, callerClientCode, clientCode, appCode)
                : Mono.just(Boolean.FALSE));
    }

    /**
     * Tells the tenant's open pages that a conversation on one of its numbers moved.
     *
     * <p>Only identifiers: the number, the customer, the message and what happened to it. The body
     * stays out on purpose, so the stream never carries more than the re-read would show.
     */
    public Mono<Void> sendConversationChanged(String appCode, String clientCode, Map<String, Object> payload) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", payload.get("eventType"));
        data.put("numberId", payload.get("whatsappPhoneNumberId"));
        data.put("sessionCode", payload.get("bridgeSessionId"));
        data.put("messageId", payload.get("metaMessageId"));
        data.put("customerWaId", payload.get("customerWaId"));
        data.put("customerPhoneNumber", payload.get("customerPhoneNumber"));
        data.put("outbound", payload.get("outbound"));

        return this.sendEvent(new MessageServerEvent()
                .setId(UUID.randomUUID().toString())
                .setTimestamp(LocalDateTime.now())
                .setEventType(EVENT_TYPE_CONVERSATION_CHANGED)
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setData(data));
    }

    /** Dropping while nobody listens, and surviving the last listener leaving. */
    @Override
    protected Many<MessageServerEvent> newSink() {
        return Sinks.many().multicast().directBestEffort();
    }

    /** Per tenant, whoever is asking. */
    @Override
    protected String getEventSinkKey(String appCode, String clientCode, BigInteger userId) {
        return appCode + ":" + clientCode;
    }
}
