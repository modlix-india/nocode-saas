package com.fincity.saas.message.service.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.message.dto.dispatch.DispatchOutbox;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import com.fincity.saas.message.service.dispatch.handler.CoreCallHandler;
import com.fincity.saas.message.service.dispatch.handler.CoreEventHandler;
import com.fincity.saas.message.service.dispatch.handler.CoreWhatsappHandler;
import com.fincity.saas.message.service.dispatch.handler.EntityProcessorCallHandler;
import com.fincity.saas.message.service.dispatch.handler.EntityProcessorWhatsappHandler;
import com.fincity.saas.message.service.event.WhatsappEventService;
import java.math.BigInteger;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Routing by {@code OWNER_SERVICE} to core, and what core receives.
 *
 * <p>The dispatcher is real and only its edges are mocked: the outbox, the broker and
 * entity-processor. That is what proves a number owned by {@code core} reaches the events exchange
 * and never entity-processor, and the reverse.
 */
class CoreEventHandlerTest {

    private static final String APP = "leadzumptwo";
    private static final String CLIENT = "FIN";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Hand-written rather than Mockito: inline mocking cannot instrument classes on newer JDKs. */
    static class FakeOutbox extends DispatchOutboxService {
        final List<String> cleared = new ArrayList<>();
        final List<String> failures = new ArrayList<>();

        FakeOutbox() {
            super(null);
        }

        @Override
        public Mono<Integer> clear(DispatchChannel channel, String eventKey, DispatchEventType eventType) {
            // On subscription, as the real one does: the dispatcher builds this Mono before it knows
            // whether the handler succeeded.
            return Mono.fromCallable(() -> {
                this.cleared.add(channel + "/" + eventKey + "/" + eventType);
                return 1;
            });
        }

        @Override
        public Mono<Integer> recordFailure(ULong id, int currentAttempts, String error) {
            this.failures.add(id + "/" + currentAttempts + "/" + error);
            return Mono.just(1);
        }
    }

    static class FakeEvents extends EventCreationService {
        final List<EventQueObject> published = new ArrayList<>();
        Mono<Boolean> answer = Mono.just(Boolean.TRUE);

        @Override
        public Mono<Boolean> createEvent(EventQueObject queObj) {
            this.published.add(queObj);
            return this.answer;
        }
    }

    private FakeOutbox outbox;
    private FakeEvents events;
    private List<String> entityProcessorCalls;
    private WhatsappEventService whatsappEvents;
    private EventDispatcher dispatcher;

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    @BeforeEach
    void setUp() {
        this.outbox = new FakeOutbox();
        this.events = new FakeEvents();
        this.entityProcessorCalls = new ArrayList<>();
        this.whatsappEvents = new WhatsappEventService(proxy(IFeignSecurityService.class, (p, m, a) -> Mono.just(false)));

        IFeignEntityProcessorService entityProcessor = proxy(IFeignEntityProcessorService.class, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return m.invoke(this, a);
            this.entityProcessorCalls.add(m.getName() + "/" + a[0] + "/" + a[1]);
            return Mono.empty();
        });

        this.dispatcher = new EventDispatcher(
                this.outbox,
                this.objectMapper,
                List.of(
                        new EntityProcessorWhatsappHandler(entityProcessor, this.objectMapper),
                        new EntityProcessorCallHandler(entityProcessor, this.objectMapper),
                        new CoreWhatsappHandler(this.events, this.whatsappEvents),
                        new CoreCallHandler(this.events)));
    }

    private Map<String, Object> asMap(Object dispatch) {
        return this.objectMapper.convertValue(dispatch, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private DispatchOutbox row(String owner, DispatchEventType type, String key, Object dispatch) {
        DispatchOutbox row = DispatchOutbox.of(owner, key, type, this.asMap(dispatch));
        row.setAppCode(APP);
        row.setClientCode(CLIENT);
        row.setId(ULong.valueOf(7));
        return row;
    }

    private static WhatsappInboundDispatch inbound() {
        return new WhatsappInboundDispatch()
                .setMetaMessageId("wamid.1")
                .setEventType(DispatchEventType.INBOUND_MESSAGE.name())
                .setWhatsappPhoneNumberId(BigInteger.valueOf(42))
                .setBridgeSessionId("SESS1")
                .setCustomerWaId("919876543210")
                .setCustomerPhoneNumber("9876543210")
                .setBodyText("hello")
                .setOutbound(false);
    }

    private EventQueObject published() {
        assertEquals(1, this.events.published.size(), "exactly one core event");
        return this.events.published.get(0);
    }

    @Test
    @DisplayName("a WhatsApp number owned by core raises WHATSAPP_INBOUND and never reaches entity-processor")
    void whatsappRoutesToCore() {
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        EventQueObject event = this.published();
        assertEquals(CoreWhatsappHandler.CORE_EVENT_NAME, event.getEventName());
        assertEquals(APP, event.getAppCode());
        assertEquals(CLIENT, event.getClientCode());
        assertNull(event.getAuthentication(), "core builds the tenant's anonymous context itself");

        assertTrue(this.entityProcessorCalls.isEmpty());
        assertEquals(List.of("WHATSAPP/wamid.1/INBOUND_MESSAGE"), this.outbox.cleared);
    }

    @Test
    @DisplayName("the core event carries the entity-processor dispatch unchanged, plus app, client, number and type")
    void whatsappPayloadShape() {
        Map<String, Object> dispatch = this.asMap(inbound());
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        Map<String, Object> data = this.published().getData();

        dispatch.forEach((k, v) -> {
            if (!CoreEventHandler.EVENT_TYPE.equals(k)) assertEquals(v, data.get(k), "field " + k + " changed on the way");
        });
        assertEquals(APP, data.get(CoreEventHandler.APP_CODE));
        assertEquals(CLIENT, data.get(CoreEventHandler.CLIENT_CODE));
        assertEquals(42, ((Number) data.get(CoreEventHandler.NUMBER_ID)).intValue());
        assertEquals("INBOUND_MESSAGE", data.get(CoreEventHandler.EVENT_TYPE));
        assertEquals("hello", data.get("bodyText"));
    }

    @Test
    @DisplayName("status, media, edit and profile events ride the same core event, told apart by eventType")
    void everyWhatsappKindIsOneCoreEvent() {
        WhatsappInboundDispatch status = inbound().setEventType(DispatchEventType.MESSAGE_STATUS.name());
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.MESSAGE_STATUS, "wamid.1", status);

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        EventQueObject event = this.published();
        assertEquals(CoreWhatsappHandler.CORE_EVENT_NAME, event.getEventName());
        assertEquals("MESSAGE_STATUS", event.getData().get(CoreEventHandler.EVENT_TYPE));
    }

    @Test
    @DisplayName("a number owned by entity-processor still goes there, and nothing is raised in core")
    void entityProcessorUnchanged() {
        DispatchOutbox row = this.row(EntityProcessorWhatsappHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        assertEquals(List.of("acceptWhatsappInbound/" + APP + "/" + CLIENT), this.entityProcessorCalls);
        assertTrue(this.events.published.isEmpty());
    }

    @Test
    @DisplayName("a call owned by core raises CALL_EVENT, with no number id and CALL_STATUS as its type")
    void callRoutesToCore() {
        CallEventDispatch call = new CallEventDispatch()
                .setProviderCallId("sid-1")
                .setEventType(DispatchEventType.CALL_STATUS.name())
                .setCallStatus("completed")
                .setCallerId("08012345678")
                .setDuration(30L);
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.CALL_STATUS, "sid-1", call);

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        EventQueObject event = this.published();
        assertEquals(CoreCallHandler.CORE_EVENT_NAME, event.getEventName());
        assertEquals("sid-1", event.getData().get("providerCallId"));
        assertEquals("completed", event.getData().get("callStatus"));
        assertTrue(event.getData().containsKey(CoreEventHandler.NUMBER_ID));
        assertNull(event.getData().get(CoreEventHandler.NUMBER_ID));
        assertEquals("CALL_STATUS", event.getData().get(CoreEventHandler.EVENT_TYPE));
        assertTrue(this.entityProcessorCalls.isEmpty());
        assertEquals(List.of("CALL/sid-1/CALL_STATUS"), this.outbox.cleared);
    }

    @Test
    @DisplayName("a dispatch with no eventType gets the channel's default")
    void defaultEventType() {
        Map<String, Object> data = new CoreCallHandler(this.events).toEventData(APP, CLIENT, Map.of("providerCallId", "x"));
        assertEquals("CALL_STATUS", data.get(CoreEventHandler.EVENT_TYPE));

        Map<String, Object> wa = new CoreWhatsappHandler(this.events, this.whatsappEvents).toEventData(APP, CLIENT, null);
        assertEquals("INBOUND_MESSAGE", wa.get(CoreEventHandler.EVENT_TYPE));
        assertNull(wa.get(CoreEventHandler.NUMBER_ID));
    }

    @Test
    @DisplayName("a broker failure keeps the outbox row for the sweeper")
    void publishFailureIsRetried() {
        this.events.answer = Mono.error(new IllegalStateException("broker down"));
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        assertEquals(List.of("7/0/broker down"), this.outbox.failures);
        assertTrue(this.outbox.cleared.isEmpty());
    }

    @Test
    @DisplayName("an unpublished event is a failure too, not a silent success")
    void unpublishedIsAFailure() {
        this.events.answer = Mono.just(Boolean.FALSE);
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row)).verifyComplete();

        assertEquals(1, this.outbox.failures.size());
        assertTrue(this.outbox.cleared.isEmpty());
    }

    @Test
    @DisplayName("open pages of the tenant hear that the conversation changed, with identifiers and no body")
    void whatsappNudgesTheTenantsPages() {
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.whatsappEvents.getEventStream(APP, CLIENT, null).take(1))
                .then(() -> this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row).block())
                .assertNext(event -> {
                    assertEquals(WhatsappEventService.EVENT_TYPE_CONVERSATION_CHANGED, event.getEventType());
                    assertEquals(APP, event.getAppCode());
                    assertEquals(CLIENT, event.getClientCode());
                    Map<String, Object> data = event.getData();
                    assertEquals("INBOUND_MESSAGE", data.get("eventType"));
                    assertEquals(42, ((Number) data.get("numberId")).intValue());
                    assertEquals("SESS1", data.get("sessionCode"));
                    assertEquals("wamid.1", data.get("messageId"));
                    assertEquals("919876543210", data.get("customerWaId"));
                    assertFalse(data.containsKey("bodyText"), "the stream must not carry the message itself");
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("an entity-processor number does not nudge the core stream")
    void entityProcessorDoesNotNudge() {
        DispatchOutbox row = this.row(EntityProcessorWhatsappHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.whatsappEvents.getEventStream(APP, CLIENT, null))
                .then(() -> this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row).block())
                .expectNoEvent(Duration.ofMillis(200))
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("a failed nudge does not fail the delivery, which would publish the event twice")
    void nudgeFailureIsSwallowed() {
        WhatsappEventService failing = new WhatsappEventService(null) {
            @Override
            public Mono<Void> sendConversationChanged(String appCode, String clientCode, Map<String, Object> payload) {
                return Mono.error(new IllegalStateException("sink gone"));
            }
        };

        StepVerifier.create(new CoreWhatsappHandler(this.events, failing).handle(APP, CLIENT, this.asMap(inbound())))
                .verifyComplete();
    }

    @Test
    @DisplayName("each channel has its own core handler, registered beside entity-processor's")
    void registryKeys() {
        assertEquals("WHATSAPP::core", new CoreWhatsappHandler(this.events, this.whatsappEvents).registryKey());
        assertEquals("CALL::core", new CoreCallHandler(this.events).registryKey());
    }

    @Test
    @DisplayName("events of another tenant are not heard")
    void otherTenantIsolated() {
        DispatchOutbox row = this.row(CoreEventHandler.SERVICE_NAME, DispatchEventType.INBOUND_MESSAGE, "wamid.1", inbound());

        StepVerifier.create(this.whatsappEvents.getEventStream(APP, "OTHER", null))
                .then(() -> this.dispatcher.deliver(MessageAccess.of(APP, CLIENT, true), row).block())
                .expectNoEvent(Duration.ofMillis(200))
                .thenCancel()
                .verify();
    }
}
