package com.fincity.saas.message.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.message.model.event.MessageServerEvent;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The tenant-wide WhatsApp stream: who may watch it, and that it keeps working as pages come and go.
 */
class WhatsappEventServiceTest {

    private static final String APP = "leadzumptwo";

    /** "managing:managed" pairs the fake security service answers true for. */
    private Set<String> manages;

    private List<String> asked;
    private WhatsappEventService service;

    @BeforeEach
    void setUp() {
        this.manages = new HashSet<>();
        this.asked = new ArrayList<>();
        // SYSTEM(1, SYS) above FIN(7) and OTHER(55); FIN above BP1(90). FIN has write access to the
        // app, SYSTEM to every app. FIN can also read "leadzump".
        Map<String, Client> clients = Map.of(
                "SYSTEM", new Client().setId(BigInteger.ONE).setCode("SYSTEM").setTypeCode("SYS"),
                "FIN", new Client().setId(BigInteger.valueOf(7)).setCode("FIN").setTypeCode("BUS"),
                "BP1", new Client().setId(BigInteger.valueOf(90)).setCode("BP1").setTypeCode("BUS"),
                "OTHER", new Client().setId(BigInteger.valueOf(55)).setCode("OTHER").setTypeCode("BUS"),
                "EVIL", new Client().setId(BigInteger.valueOf(66)).setCode("EVIL").setTypeCode("BUS"),
                "SUB", new Client().setId(BigInteger.valueOf(91)).setCode("SUB").setTypeCode("BUS"));
        Map<BigInteger, List<BigInteger>> chains = Map.of(
                BigInteger.ONE, List.of(BigInteger.ONE),
                BigInteger.valueOf(7), List.of(BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(90), List.of(BigInteger.valueOf(90), BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(55), List.of(BigInteger.valueOf(55), BigInteger.ONE),
                BigInteger.valueOf(66), List.of(BigInteger.valueOf(66), BigInteger.ONE),
                BigInteger.valueOf(91),
                List.of(BigInteger.valueOf(91), BigInteger.valueOf(90), BigInteger.valueOf(7), BigInteger.ONE));

        IFeignSecurityService security = (IFeignSecurityService) Proxy.newProxyInstance(
                IFeignSecurityService.class.getClassLoader(), new Class<?>[] {IFeignSecurityService.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "doesClientManageClientCode": {
                            String pair = a[0] + ":" + a[1];
                            this.asked.add(pair);
                            return Mono.just(this.manages.contains(pair));
                        }
                        case "getClientByCode":
                            return Mono.justOrEmpty(clients.get((String) a[0]));
                        case "getClientHierarchy":
                            return Mono.justOrEmpty(chains.get((BigInteger) a[0]));
                        case "hasWriteAccess":
                            return Mono.just("SYSTEM".equals(a[1]) || (APP.equals(a[0]) && "FIN".equals(a[1])));
                        case "hasReadAccess":
                            return Mono.just(("leadzump".equals(a[0]) && "FIN".equals(a[1]))
                                    || (APP.equals(a[0]) && List.of("FIN", "BP1", "SUB", "SYSTEM").contains(a[1])));
                        default:
                            throw new UnsupportedOperationException(m.getName());
                    }
                });
        this.service = new WhatsappEventService(security);
    }

    private static ContextAuthentication userOf(String clientCode) {
        ContextUser user = new ContextUser()
                .setId(BigInteger.valueOf(110))
                .setClientId(BigInteger.ONE)
                .setUserName("u")
                .setStringAuthorities(List.of("Authorities.Logged_IN"));
        ContextAuthentication ca = new ContextAuthentication().setUser(user);
        ca.setAuthenticated(true);
        return ca.setClientCode(clientCode)
                .setLoggedInFromClientCode(clientCode)
                .setUrlClientCode(clientCode)
                .setUrlAppCode(APP)
                .setAccessTokenExpiryAt(LocalDateTime.MAX);
    }

    private Flux<MessageServerEvent> streamAs(String callerClient, String tenant) {
        return this.streamAs(callerClient, APP, tenant);
    }

    private Flux<MessageServerEvent> streamAs(String callerClient, String app, String tenant) {
        return this.service
                .getConversationStream(app, tenant)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(userOf(callerClient)))));
    }

    private Mono<Void> nudge(String tenant) {
        return this.service.sendConversationChanged(APP, tenant, Map.of("eventType", "INBOUND_MESSAGE"));
    }

    @Test
    @DisplayName("the tenant's own users hear its conversations")
    void ownTenant() {
        StepVerifier.create(this.streamAs("FIN", "FIN").take(1))
                .then(() -> this.nudge("FIN").block())
                .assertNext(e -> assertEquals(WhatsappEventService.EVENT_TYPE_CONVERSATION_CHANGED, e.getEventType()))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertTrue(this.asked.isEmpty(), "own tenant needs no lookup");
    }

    @Test
    @DisplayName("an unrelated client is refused, not handed an empty stream")
    void unrelatedRefused() {
        StepVerifier.create(this.streamAs("EVIL", "FIN"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("a partner may watch its direct managing client; a client that manages the tenant may not")
    void partnerUpOnly() {
        this.manages.add("FIN:BP1");
        this.manages.add("SYSTEM:FIN");

        StepVerifier.create(this.streamAs("BP1", "FIN").take(1))
                .then(() -> this.nudge("FIN").block())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // Managing a client is not hearing its conversations: FIN -> BP1 and SYSTEM -> FIN are refused.
        StepVerifier.create(this.streamAs("FIN", "BP1"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));
        StepVerifier.create(this.streamAs("SYSTEM", "FIN"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("only the direct managing client: a grandparent is refused even with write access")
    void grandparentRefused() {
        this.manages.add("FIN:BP1");
        this.manages.add("FIN:SUB");
        this.manages.add("BP1:SUB");

        StepVerifier.create(this.streamAs("SUB", "FIN"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("an app the caller's client has no access to is refused even when it is the request's app")
    void requestAppWithoutAccessRefused() {
        StepVerifier.create(this.streamAs("OTHER", "OTHER"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("a partner never receives the customer phone fields; the tenant's own users do")
    void partnerGetsNoPhones() {
        this.manages.add("FIN:BP1");
        Map<String, Object> payload = Map.of("eventType", "INBOUND_MESSAGE", "whatsappPhoneNumberId", "N1",
                "metaMessageId", "wamid.1", "customerWaId", "919876543210", "customerPhoneNumber", "9876543210");

        Flux<MessageServerEvent> partner = this.streamAs("BP1", "FIN").take(1);
        Flux<MessageServerEvent> own = this.streamAs("FIN", "FIN").take(1);

        StepVerifier.create(Flux.merge(partner.map(e -> Map.entry("BP1", e)), own.map(e -> Map.entry("FIN", e)))
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue))
                .then(() -> this.service.sendConversationChanged(APP, "FIN", payload).block())
                .assertNext(byCaller -> {
                    Map<String, Object> p = byCaller.get("BP1").getData();
                    assertTrue(!p.containsKey("customerWaId") && !p.containsKey("customerPhoneNumber"), p.toString());
                    assertEquals("wamid.1", p.get("messageId"));
                    assertEquals("N1", p.get("numberId"));
                    Map<String, Object> f = byCaller.get("FIN").getData();
                    assertEquals("919876543210", f.get("customerWaId"));
                    assertEquals("9876543210", f.get("customerPhoneNumber"));
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("a CUSTOMER-level caller is a partner even on its own client's stream")
    void customerLevelIsPartner() {
        ContextAuthentication ca = userOf("BP1").setClientLevelType("CUSTOMER");
        assertTrue(WhatsappEventService.isPartner(ca, "BP1"));
        assertTrue(WhatsappEventService.isPartner(userOf("BP1"), "FIN"));
        assertTrue(!WhatsappEventService.isPartner(userOf("FIN"), "FIN"));
    }

    @Test
    @DisplayName("nobody watches SYSTEM's conversations, although SYSTEM manages every client")
    void systemRefused() {
        this.manages.add("SYSTEM:FIN");
        this.manages.add("SYSTEM:BP1");
        this.manages.add("FIN:BP1");

        for (String caller : List.of("FIN", "BP1", "EVIL")) {
            StepVerifier.create(this.streamAs(caller, "SYSTEM"))
                    .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                    .verify(Duration.ofSeconds(5));
        }
    }

    @Test
    @DisplayName("an app the caller's client cannot read is refused, even for its own client")
    void unreadableAppRefused() {
        StepVerifier.create(this.streamAs("FIN", "someoneElsesApp", "FIN"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));

        StepVerifier.create(this.streamAs("FIN", "leadzump", "FIN").take(1))
                .then(() -> this.service.sendConversationChanged("leadzump", "FIN", Map.of("eventType", "X")).block())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("a partner does not watch the managing client's numbers of an app the managing client cannot edit")
    void partnerRefusedForAnotherApp() {
        this.manages.add("FIN:BP1");

        // BP1 cannot read "leadzump" in this world, and FIN has no write access to it either.
        StepVerifier.create(this.streamAs("BP1", "leadzump", "FIN"))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify(Duration.ofSeconds(5));
    }

    /**
     * The call streams' buffered sink terminates when its last subscriber leaves. For a stream
     * shared by every page of a tenant that would end live updates the first time everyone closed
     * the tab.
     */
    @Test
    @DisplayName("the stream still works after every page has left and a new one opens")
    void survivesLastSubscriberLeaving() {
        StepVerifier.create(this.service.getEventStream(APP, "FIN", null).take(1))
                .then(() -> this.nudge("FIN").block())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        this.nudge("FIN").block(); // nobody listening: dropped, not buffered

        StepVerifier.create(this.service.getEventStream(APP, "FIN", null).take(1))
                .then(() -> this.nudge("FIN").block())
                .assertNext(e -> assertEquals("FIN", e.getClientCode()))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("one stream per tenant, whichever user asks")
    void keyIgnoresUser() {
        assertEquals(
                this.service.getEventSinkKey(APP, "FIN", BigInteger.ONE),
                this.service.getEventSinkKey(APP, "FIN", BigInteger.TEN));
    }
}
