package com.fincity.saas.entity.processor.service.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.message.WhatsappStreamEvent;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

/**
 * Pushes "this deal changed" to the browsers the event is addressed to.
 *
 * <h2>Why this cannot be an in-process map, unlike the two SSE services already in this codebase</h2>
 *
 * <p>Production runs <b>two</b> entity-processor containers per colour. A browser opens its stream
 * through the load balancer and lands on one of them. The inbound WhatsApp event arrives through the
 * same load balancer, from the message service's dispatch outbox, and lands on <b>either</b> of
 * them. So the instance that writes the message row is, half the time, not the instance holding the
 * browser that wants to hear about it.
 *
 * <p>No amount of session stickiness fixes that. Stickiness pins a browser to an instance, and the
 * event was never in the browser's request.
 *
 * <p>So this service does not deliver events. It <b>publishes</b> them, and every instance relays
 * whatever arrives to its own subscribers. The mechanism is the one {@code CacheService} already
 * uses to broadcast cache evictions across ui, core and security, down to the same Lettuce beans.
 *
 * <h2>Who receives an event</h2>
 *
 * <p>Two filters, in this order.
 *
 * <p><b>Addressing, always.</b> {@link #addressed} tests whether the event names this connection's
 * user. The list was resolved at publish by {@code TicketAudienceService}, which inverts the
 * condition {@code TicketDAO} puts on every deal read. Nothing about the rule is evaluated here.
 *
 * <p>Two earlier designs are worth remembering. A tenant-wide broadcast where each browser fetched
 * the ticket to discover whether it was allowed to care was correct but cost one authenticated read
 * per open browser per event. Re-evaluating the rule per connection was cheap but wrong: it copied
 * one branch of a rule that has three, ignored per-product read rules, and compared the wrong client
 * code for business partners.
 *
 * <p><b>Interest, for status receipts only.</b> A delivery or read receipt is worth nothing to
 * anyone not looking at that thread, and receipts are the highest-volume kind by some distance. A
 * browser may name the deal it is watching through {@link #watch}, and a browser that has named one
 * gets {@link WhatsappStreamEvent#KIND_STATUS} for that deal alone. A browser that has named
 * <i>nothing</i> still gets all of them, so the feature degrades to the previous behaviour if the
 * client never calls {@code watch}, rather than silently going quiet.
 *
 * <h2>The staleness this accepts</h2>
 *
 * <p>The audience is a snapshot taken when the message was stored. A deal reassigned immediately
 * afterwards is routed by who owned it a moment earlier. The window is one event wide, the next
 * event corrects it, and the thread itself is always fetched through the real read, so nothing here
 * should be relied on for anything but a refetch nudge.
 *
 * <h2>Two deliberate differences from AbstractServerSentEventService</h2>
 *
 * <p><b>A sink per connection, not per user.</b> That service keys sinks by
 * {@code appCode:clientCode:userId} and never removes them, so every distinct user who ever
 * connected holds a sink for the life of the JVM. Keying by connection means {@code doFinally} can
 * remove it when the browser goes away, which turns a leak that needs a sweeper into one that cannot
 * happen. This codebase has already lost two services to unbounded caches; a third was not
 * appealing.
 *
 * <p><b>Identity from the security context.</b> That service takes {@code userId} as a request
 * parameter, so any authenticated caller can subscribe to another user's stream by editing the URL.
 * Nothing here is taken from the caller except the app code.
 */
@Service
public class WhatsappEventService extends RedisPubSubAdapter<String, String> implements IProcessorAccessService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappEventService.class);

    /**
     * Comment frames, so an idle stream is not mistaken for a dead one.
     *
     * <p>Fifteen seconds because that is what {@code InAppNotificationService} settled on and both
     * streams cross the same proxies. Without it nginx closes a quiet connection and the browser
     * reconnects in a loop that looks, from the outside, exactly like a working feature.
     */
    private static final Duration HEARTBEAT = Duration.ofSeconds(15);

    /**
     * One entry per open browser connection. Removed by {@code doFinally}, so the size of this map
     * is the number of people currently looking at the product.
     */
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    /**
     * A connected browser and the access it was opened with.
     *
     * <p>{@code watching} is the one mutable part: it is set by {@link #watch} when the user opens a
     * deal's thread and cleared when they leave it, so it changes many times over a connection's
     * life while everything else is fixed at subscribe.
     */
    private record Subscriber(
            String appCode,
            String clientCode,
            BigInteger userId,
            Many<WhatsappStreamEvent> sink,
            AtomicReference<BigInteger> watching) {}

    private final ObjectMapper objectMapper;

    @Getter
    private final ProcessorMessageResourceService msgService;

    @Getter
    private final IFeignSecurityService securityService;

    /**
     * All three Redis beans are optional, and are declared {@code @Nullable} rather than as fields so
     * this stays constructor-injected like everything else in the service layer. They are absent on a
     * machine with no Redis, which is a supported way to run this locally.
     */
    private final RedisPubSubAsyncCommands<String, String> subAsyncCommand;

    private final RedisPubSubAsyncCommands<String, String> pubAsyncCommand;

    private final StatefulRedisPubSubConnection<String, String> subConnect;

    private final String channel;

    public WhatsappEventService(
            ObjectMapper objectMapper,
            ProcessorMessageResourceService msgService,
            IFeignSecurityService securityService,
            @Nullable @Qualifier("subRedisAsyncCommand") RedisPubSubAsyncCommands<String, String> subAsyncCommand,
            @Nullable @Qualifier("pubRedisAsyncCommand") RedisPubSubAsyncCommands<String, String> pubAsyncCommand,
            @Nullable StatefulRedisPubSubConnection<String, String> subConnect,
            @Value("${processor.whatsapp.event.channel:whatsappEventChannel}") String channel) {
        this.objectMapper = objectMapper;
        this.msgService = msgService;
        this.securityService = securityService;
        this.subAsyncCommand = subAsyncCommand;
        this.pubAsyncCommand = pubAsyncCommand;
        this.subConnect = subConnect;
        this.channel = channel;
    }

    @PostConstruct
    public void subscribeToChannel() {
        if (this.subAsyncCommand == null || this.subConnect == null) {
            // Not fatal, and it must not be: a developer running without Redis still gets working
            // streams, because publish() falls back to delivering in process. What they do not get
            // is the cross-instance relay, which is exactly the thing a single-instance machine
            // cannot exercise anyway.
            logger.warn("No Redis pub/sub connection; WhatsApp events will not cross instances");
            return;
        }

        this.subAsyncCommand.subscribe(this.channel);
        this.subConnect.addListener(this);
    }

    /**
     * The authenticated entry point: a stream scoped to what the calling user may see.
     *
     * <h2>Why the app code is a request parameter</h2>
     *
     * <p>{@code EventSource} cannot set request headers. That is a limitation of the browser API,
     * not a shortcut here. {@code ContextAuthentication.urlAppCode} is populated purely from the
     * {@code appCode} header ({@code JWTTokenFilter}), so on an SSE request it is null. This is why
     * the notification service's stream takes {@code ?appCode=} as well.
     *
     * <p>That null matters more here than it looks. {@code IProcessorAccessService} resolves the
     * subOrg with {@code getUserSubOrgInternal(userId, urlAppCode, clientId)}, so leaving it null
     * would resolve the org tree against no app and route on an empty set: every browser would fall
     * back to whatever {@code managingClientIds} allowed and quietly hear too little. The parameter
     * is therefore written onto the context before access is resolved.
     *
     * <p>The client code is never caller-supplied; it comes from the token, via the same
     * {@code hasAccess()} every deal query uses.
     */
    public Flux<ServerSentEvent<WhatsappStreamEvent>> streamForCurrentUser(String appCode) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .filter(ca -> ca != null && ca.isAuthenticated())
                .doOnNext(ca -> {
                    if (ca.getUrlAppCode() == null) ca.setUrlAppCode(appCode);
                })
                .flatMap(ca -> this.hasAccess())
                .flatMapMany(access -> this.stream(appCode, access))
                .switchIfEmpty(Flux.defer(
                        () -> Flux.error(new GenericException(HttpStatus.FORBIDDEN, "Login required"))));
    }

    /**
     * A browser's stream.
     *
     * <p>The first frame is a {@link WhatsappStreamEvent#KIND_INIT} carrying the connection id, so
     * the browser can name itself to {@link #watch} later. It is emitted before the merge with the
     * live sink so it cannot be overtaken by a real event.
     *
     * <p>The returned Flux unregisters on termination, whether that is the browser navigating away,
     * the connection dropping, or the container shutting down. {@code doFinally} covers all three; a
     * {@code doOnCancel} would cover only the first.
     */
    public Flux<ServerSentEvent<WhatsappStreamEvent>> stream(String appCode, ProcessorAccess access) {

        String connectionId = UUID.randomUUID().toString();

        // onBackpressureBuffer rather than directBestEffort: a browser on a slow link should fall
        // behind and catch up, not silently miss the event that would have told it to refetch.
        Many<WhatsappStreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Nothing about the caller's access is cached here any more. An earlier version held the
        // subscriber's whole sub-organisation, which for a senior manager in a deep organisation is
        // thousands of ids per connection per tab, and which went stale the moment anybody moved
        // team. Entitlement is decided once at publish instead.
        Subscriber subscriber =
                new Subscriber(appCode, access.getClientCode(), toBig(access.getUserId()), sink, new AtomicReference<>());

        this.subscribers.put(connectionId, subscriber);

        Flux<ServerSentEvent<WhatsappStreamEvent>> init = Flux.just(ServerSentEvent.builder(
                        new WhatsappStreamEvent()
                                .setKind(WhatsappStreamEvent.KIND_INIT)
                                .setConnectionId(connectionId)
                                .setAppCode(appCode)
                                .setClientCode(access.getClientCode())
                                .setAt(System.currentTimeMillis()))
                .event("whatsapp")
                .build());

        Flux<ServerSentEvent<WhatsappStreamEvent>> events = sink.asFlux()
                .map(event -> ServerSentEvent.builder(event)
                        .event("whatsapp")
                        .id(String.valueOf(event.getAt()))
                        .build());

        Flux<ServerSentEvent<WhatsappStreamEvent>> heartbeat = Flux.interval(HEARTBEAT, HEARTBEAT)
                .map(i -> ServerSentEvent.<WhatsappStreamEvent>builder()
                        .comment("keepalive")
                        .build());

        return init.concatWith(Flux.merge(events, heartbeat)).doFinally(signal -> {
            this.subscribers.remove(connectionId);
            logger.debug(
                    "WhatsApp stream closed for {}/{} ({}), {} remaining",
                    access.getClientCode(),
                    appCode,
                    signal,
                    this.subscribers.size());
        });
    }

    /**
     * Records which deal a browser is looking at, so status receipts can be narrowed to it.
     *
     * <p>Authorised by ownership of the connection, not of the deal: a caller may only touch a
     * connection their own user opened, and the worst a lie about {@code ticketId} achieves is
     * receiving fewer events, or receiving a {@code STATUS} ping for a deal id they made up and
     * cannot read anyway. Addressing is still enforced on the way out by {@link #addressed}, so this
     * narrows and never widens.
     *
     * <p>A null {@code ticketId} clears the interest, which restores "send me every receipt I am
     * entitled to". That is what the inbox does when a conversation is closed.
     */
    public Mono<Boolean> watch(String connectionId, ULong ticketId) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .filter(ca -> ca != null && ca.isAuthenticated())
                .map(ca -> {
                    Subscriber subscriber = this.subscribers.get(connectionId);

                    if (subscriber == null) return Boolean.FALSE;

                    BigInteger callerId = ca.getUser() == null ? null : ca.getUser().getId();
                    if (callerId == null
                            || !callerId.equals(subscriber.userId())
                            || !ca.getClientCode().equals(subscriber.clientCode())) {
                        logger.warn("Rejected a watch on a connection belonging to somebody else");
                        return Boolean.FALSE;
                    }

                    subscriber.watching().set(ticketId == null ? null : ticketId.toBigInteger());
                    return Boolean.TRUE;
                })
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<Void> publishMessage(String appCode, String clientCode, TicketRouting routing) {
        return this.publish(appCode, clientCode, routing, WhatsappStreamEvent.KIND_MESSAGE);
    }

    public Mono<Void> publishStatus(String appCode, String clientCode, TicketRouting routing) {
        return this.publish(appCode, clientCode, routing, WhatsappStreamEvent.KIND_STATUS);
    }

    public Mono<Void> publishTask(String appCode, String clientCode, TicketRouting routing) {
        return this.publish(appCode, clientCode, routing, WhatsappStreamEvent.KIND_TASK);
    }

    /**
     * A resolved event, ready to address.
     *
     * <p>{@code recipients} is the audience already worked out by {@code TicketAudienceService};
     * this service does not resolve it, so there is no way to publish without having decided who may
     * hear it.
     */
    public record TicketRouting(
            ULong ticketId,
            ULong productId,
            String dealName,
            String dealCode,
            String body,
            List<BigInteger> recipients) {}

    /**
     * Hands an event to every instance, including this one.
     *
     * <p>Always returns empty rather than failing. This hangs off the write path, and a stream that
     * cannot be told about a message is a stale screen; a write that fails because the stream could
     * not be told is a lost customer message. The two are not close in severity, so nothing here is
     * allowed to break the caller.
     */
    private Mono<Void> publish(String appCode, String clientCode, TicketRouting routing, String kind) {

        if (appCode == null || clientCode == null || routing == null || routing.ticketId() == null)
            return Mono.empty();

        // Nobody to tell is not an error: a deal whose assignee has left and whose product carries
        // no rules genuinely has no live audience. Publishing it anyway would put a customer's
        // message on a shared channel for no reader.
        if (routing.recipients() == null || routing.recipients().isEmpty()) return Mono.empty();

        WhatsappStreamEvent event = new WhatsappStreamEvent()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setTicketId(routing.ticketId().toBigInteger())
                .setProductId(toBig(routing.productId()))
                .setRecipients(routing.recipients())
                .setDealName(routing.dealName())
                .setDealCode(routing.dealCode())
                .setBody(routing.body())
                .setKind(kind)
                .setAt(System.currentTimeMillis());

        if (this.pubAsyncCommand == null) {
            // No Redis. Deliver in process so a single-instance developer machine still behaves.
            this.fanOut(event);
            return Mono.empty();
        }

        String payload;
        try {
            payload = this.objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            logger.error("Could not serialise a WhatsApp stream event for deal {}", routing.ticketId(), e);
            return Mono.empty();
        }

        // Published only, never also fanned out locally: this instance is subscribed to the same
        // channel, so Redis hands the event back and message() delivers it. Doing both would show
        // every message twice to whoever happened to be on the publishing instance.
        return Mono.fromCompletionStage(this.pubAsyncCommand.publish(this.channel, payload))
                .doOnError(e -> logger.error("Could not publish a WhatsApp stream event", e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    @Override
    public void message(String channel, String message) {

        if (channel == null || !channel.equals(this.channel)) return;

        WhatsappStreamEvent event;
        try {
            event = this.objectMapper.readValue(message, WhatsappStreamEvent.class);
        } catch (Exception e) {
            logger.error("Ignoring an unreadable WhatsApp stream event", e);
            return;
        }

        this.fanOut(event);
    }

    /**
     * Emits to the connections this instance holds that the event names.
     *
     * <p>Most instances match nothing and do nothing, which is the design working rather than a
     * problem: the event was broadcast precisely because nobody knows which instance is holding the
     * browser.
     */
    private void fanOut(WhatsappStreamEvent event) {

        if (event.getClientCode() == null || event.getAppCode() == null) return;

        this.subscribers.values().stream()
                .filter(s -> event.getClientCode().equals(s.clientCode())
                        && event.getAppCode().equals(s.appCode()))
                .filter(s -> this.addressed(s, event))
                .filter(s -> this.interested(s, event))
                .forEach(s -> {
                    Sinks.EmitResult result = s.sink().tryEmitNext(event);
                    if (result.isFailure())
                        logger.warn("Dropped a WhatsApp stream event for {}: {}", event.getClientCode(), result);
                });
    }

    /**
     * Whether this event names this browser's user.
     *
     * <p>The entire routing decision. {@code recipients} was resolved at publish by inverting the
     * deal read rule; nothing is re-derived here, because a second evaluation of that rule is
     * exactly what this replaced.
     *
     * <p>Closed on a missing list, so a publisher that forgets to resolve an audience makes the
     * feature go quiet rather than send a customer's message to a whole tenant.
     */
    private boolean addressed(Subscriber subscriber, WhatsappStreamEvent event) {

        List<BigInteger> recipients = event.getRecipients();

        return recipients != null && subscriber.userId() != null && recipients.contains(subscriber.userId());
    }

    /**
     * Whether this browser asked about this deal, for the kinds where that matters.
     *
     * <p>Only {@code STATUS} is narrowed. A new message has to reach whoever can see the deal even
     * when they are looking elsewhere, because that is what raises the toast; a receipt has nothing
     * to say to someone who is not watching the ticks change.
     */
    private boolean interested(Subscriber subscriber, WhatsappStreamEvent event) {

        if (!WhatsappStreamEvent.KIND_STATUS.equals(event.getKind())) return true;

        BigInteger watching = subscriber.watching().get();

        // Nothing declared means the client has not been taught to declare, so behave as before.
        return watching == null || watching.equals(event.getTicketId());
    }

    private static BigInteger toBig(ULong value) {
        return value == null ? null : value.toBigInteger();
    }

    /** Test seam and a diagnostic: how many browsers this instance is currently holding. */
    public int openConnections() {
        return this.subscribers.size();
    }

    /** Test seam: how many of them belong to one tenant. */
    public long openConnections(String appCode, String clientCode) {
        return this.subscribers.values().stream()
                .filter(s -> s.clientCode().equals(clientCode) && s.appCode().equals(appCode))
                .count();
    }

    /** Test seam, so the relay can be exercised without a Redis server in the way. */
    void deliverLocally(WhatsappStreamEvent event) {
        this.fanOut(event);
    }
}
