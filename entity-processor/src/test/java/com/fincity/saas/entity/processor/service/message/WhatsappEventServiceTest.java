package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.message.WhatsappStreamEvent;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.util.function.Tuples;

/**
 * The relay: who an already-addressed event reaches, and what happens to the connection.
 *
 * <p>Note what is <b>not</b> tested here any more. This service used to decide entitlement itself,
 * by re-evaluating the deal read rule per connection, and the bulk of this file asserted that rule.
 * It got the rule wrong in four ways, so the decision moved to {@code TicketAudienceService}, which
 * resolves an audience once at publish by inverting the real condition. What is left here is
 * delivery: does an event reach the people it names, does it reach nobody else, and does the
 * connection clean up after itself.
 *
 * <p>Constructed with no Redis, which is both a supported local configuration and the only way to
 * exercise the fan-out without standing a server up. What Redis adds is getting the event from one
 * JVM to another; {@code deliverLocally} stands in for "a message arrived on the channel". The
 * cross-instance behaviour cannot be proven in one process and is a two-instance manual check.
 */
class WhatsappEventServiceTest {

    private static final BigInteger ALICE = BigInteger.valueOf(101);
    private static final BigInteger BOB = BigInteger.valueOf(202);
    private static final BigInteger ACME_CLIENT = BigInteger.valueOf(28);

    private WhatsappEventService service;

    @BeforeEach
    void setUp() {
        this.service =
                new WhatsappEventService(new ObjectMapper(), null, null, null, null, null, "whatsappEventChannel");
    }

    private static ProcessorAccess access(String clientCode, BigInteger userId) {

        ContextAuthentication ca = new ContextAuthentication()
                .setClientCode(clientCode)
                .setLoggedInFromClientCode(clientCode)
                .setLoggedInFromClientId(ACME_CLIENT)
                .setClientLevelType("OWNER");

        ProcessorAccess.UserInheritanceInfo inherit = ProcessorAccess.UserInheritanceInfo.of(
                ca, Tuples.of(List.of(userId), List.<BigInteger>of(), new Client()));

        return ProcessorAccess.of("leadzump", clientCode, true, new ContextUser().setId(userId), inherit);
    }

    private static WhatsappStreamEvent event(String clientCode, long ticketId, String kind, BigInteger... recipients) {
        return new WhatsappStreamEvent()
                .setAppCode("leadzump")
                .setClientCode(clientCode)
                .setTicketId(BigInteger.valueOf(ticketId))
                .setRecipients(List.of(recipients))
                .setDealName("Priya Nair")
                .setDealCode("TKT-9")
                .setBody("Can you send the brochure?")
                .setKind(kind)
                .setAt(System.currentTimeMillis());
    }

    /** Collects the data frames, skipping heartbeat comments and the opening INIT handshake. */
    private Disposable collect(ProcessorAccess access, List<ServerSentEvent<WhatsappStreamEvent>> into) {
        return this.service
                .stream("leadzump", access)
                .filter(sse -> sse.data() != null)
                .filter(sse -> !WhatsappStreamEvent.KIND_INIT.equals(sse.data().getKind()))
                .doOnNext(into::add)
                .subscribe();
    }

    @Test
    @DisplayName("A stream opens by telling the browser its connection id")
    void handsOutAConnectionId() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.service
                .stream("leadzump", access("ACME", ALICE))
                .filter(sse -> sse.data() != null)
                .doOnNext(received::add)
                .subscribe();

        // Without this the browser cannot name its own connection, and watch() has nothing to key on.
        assertEquals(1, received.size());
        assertEquals(WhatsappStreamEvent.KIND_INIT, received.get(0).data().getKind());
        assertFalse(received.get(0).data().getConnectionId().isBlank());

        alice.dispose();
    }

    @Test
    @DisplayName("An event reaches a user it names")
    void deliversToAnAddressee() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        this.service.deliverLocally(event("ACME", 3433, WhatsappStreamEvent.KIND_MESSAGE, ALICE, BOB));

        assertEquals(1, received.size());
        assertEquals("whatsapp", received.get(0).event());
        assertEquals(BigInteger.valueOf(3433), received.get(0).data().getTicketId());
        assertEquals("Priya Nair", received.get(0).data().getDealName());
        assertEquals("Can you send the brochure?", received.get(0).data().getBody());

        alice.dispose();
    }

    @Test
    @DisplayName("An event does not reach a user it does not name")
    void doesNotDeliverToAnyoneElse() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        // Same tenant, same app, a colleague the audience did not include. This event carries the
        // lead's name and their message, so arriving here would be a disclosure, not a nuisance.
        this.service.deliverLocally(event("ACME", 3433, WhatsappStreamEvent.KIND_MESSAGE, BOB));

        assertTrue(received.isEmpty());

        alice.dispose();
    }

    @Test
    @DisplayName("An event naming nobody reaches nobody")
    void failsClosedOnAnEmptyAudience() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        this.service.deliverLocally(
                event("ACME", 11, WhatsappStreamEvent.KIND_MESSAGE).setRecipients(null));
        this.service.deliverLocally(event("ACME", 12, WhatsappStreamEvent.KIND_MESSAGE));

        // A publisher that fails to resolve an audience must make the feature go quiet, never wide.
        assertTrue(received.isEmpty());

        alice.dispose();
    }

    @Test
    @DisplayName("An event for another tenant never reaches this one, even if it names this user")
    void doesNotCrossTenants() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        // User ids are unique platform-wide, so the tenant check is belt and braces. It stays
        // because one app code shared across tenants is the normal arrangement here, and a bug that
        // reuses an id across clients should not become a cross-tenant disclosure.
        this.service.deliverLocally(event("OTHERCO", 999, WhatsappStreamEvent.KIND_MESSAGE, ALICE));
        this.service.deliverLocally(event("ACME", 3433, WhatsappStreamEvent.KIND_MESSAGE, ALICE));

        assertEquals(1, received.size());
        assertEquals(BigInteger.valueOf(3433), received.get(0).data().getTicketId());

        alice.dispose();
    }

    @Test
    @DisplayName("An event for another app on the same tenant is not delivered either")
    void doesNotCrossApps() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        this.service.deliverLocally(
                event("ACME", 55, WhatsappStreamEvent.KIND_MESSAGE, ALICE).setAppCode("cxapp"));

        assertTrue(received.isEmpty());

        alice.dispose();
    }

    @Test
    @DisplayName("Without a declared interest, every receipt still arrives")
    void statusUnnarrowedByDefault() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        // The narrowing is an optimisation the client opts into. A client that never calls watch()
        // must keep working exactly as it did, or shipping the server ahead of the pages silently
        // stops delivery receipts.
        this.service.deliverLocally(event("ACME", 1, WhatsappStreamEvent.KIND_STATUS, ALICE));
        this.service.deliverLocally(event("ACME", 2, WhatsappStreamEvent.KIND_STATUS, ALICE));

        assertEquals(2, received.size());

        alice.dispose();
    }

    @Test
    @DisplayName("A new message arrives whether or not you are watching that deal")
    void messagesAreNeverNarrowed() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        // Narrowing MESSAGE would kill the toast, which exists precisely to tell you about the deal
        // you are not looking at.
        this.service.deliverLocally(event("ACME", 2, WhatsappStreamEvent.KIND_MESSAGE, ALICE));

        assertEquals(1, received.size());

        alice.dispose();
    }

    @Test
    @DisplayName("A closed connection is forgotten, so the registry cannot grow without bound")
    void unregistersOnTermination() {

        assertEquals(0, this.service.openConnections());

        Disposable one = this.service.stream("leadzump", access("ACME", ALICE)).subscribe();
        Disposable two = this.service.stream("leadzump", access("ACME", BOB)).subscribe();
        assertEquals(2, this.service.openConnections());
        assertEquals(2, this.service.openConnections("leadzump", "ACME"));

        one.dispose();
        assertEquals(1, this.service.openConnections(), "cancelling a stream must remove its sink");

        two.dispose();
        assertEquals(
                0,
                this.service.openConnections(),
                "nothing may survive the last browser leaving; this is the leak the message service has");
    }

    @Test
    @DisplayName("Two events on the same deal are distinguishable")
    void eventsCarryAChangingStamp() throws InterruptedException {

        WhatsappStreamEvent first = event("ACME", 3433, WhatsappStreamEvent.KIND_MESSAGE, ALICE);
        Thread.sleep(2);
        WhatsappStreamEvent second = event("ACME", 3433, WhatsappStreamEvent.KIND_MESSAGE, ALICE);

        // The UI treats Store.waPing as changed or not changed. Two identical pings on one deal would
        // be indistinguishable without this, and the second message would not trigger a refresh.
        assertNotEquals(first.getAt(), second.getAt());
        assertTrue(second.getAt() > first.getAt());
    }

    @Test
    @DisplayName("A readable payload on the channel is delivered, recipients and all")
    void relaysWhatArrivesOnTheChannel() throws Exception {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        String payload = new ObjectMapper()
                .writeValueAsString(event("ACME", 3434, WhatsappStreamEvent.KIND_STATUS, ALICE));
        this.service.message("whatsappEventChannel", payload);

        // Serialisation round trip matters: the recipient list is the routing, so a field that does
        // not survive JSON would deliver to nobody across instances while working locally.
        assertEquals(1, received.size());
        assertEquals("STATUS", received.get(0).data().getKind());
        assertEquals(BigInteger.valueOf(3434), received.get(0).data().getTicketId());
        assertEquals(List.of(ALICE), received.get(0).data().getRecipients());

        alice.dispose();
    }

    @Test
    @DisplayName("An unreadable channel payload is dropped, not thrown")
    void survivesRubbishOnTheChannel() {

        Disposable alice =
                this.service.stream("leadzump", access("ACME", ALICE)).subscribe();

        // A different service, or an older version of this one, publishing something unexpected must
        // not take live subscribers down with it.
        this.service.message("whatsappEventChannel", "{not json");
        this.service.message("whatsappEventChannel", "");

        assertEquals(1, this.service.openConnections(), "a bad payload must not kill live connections");

        alice.dispose();
    }

    @Test
    @DisplayName("Traffic on another channel is ignored")
    void ignoresOtherChannels() {

        List<ServerSentEvent<WhatsappStreamEvent>> received = new CopyOnWriteArrayList<>();
        Disposable alice = this.collect(access("ACME", ALICE), received);

        // The eviction channel carries "cacheName:key" strings on the same Redis. Reacting to those
        // would turn every cache eviction into a thread refresh for every open browser.
        this.service.message("evictionChannel", "cmn-TicketCache:3433");

        assertTrue(received.isEmpty());

        alice.dispose();
    }
}
