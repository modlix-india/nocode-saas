package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Exercises the service against a stubbed exchange function rather than a
 * socket, so the assertions can be about the request we send as well as our
 * handling of the reply.
 */
class CloudflareCustomHostnameServiceTest {

    private static final String TOKEN = "cf-token";
    private static final String ZONE = "zone123";
    private static final String HOST = "rajaira.com";

    private final List<ClientRequest> requests = new ArrayList<>();

    /** Replies with the given JSON, in order, recording every request made. */
    private CloudflareCustomHostnameService serviceReplying(String... jsonPerCall) {
        return this.build(request -> {
            this.requests.add(request);
            String json = jsonPerCall[Math.min(this.requests.size() - 1, jsonPerCall.length - 1)];
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(json)
                    .build());
        }, TOKEN, ZONE);
    }

    private CloudflareCustomHostnameService serviceFailing() {
        return this.build(request -> {
            this.requests.add(request);
            return Mono.error(new java.net.ConnectException("refused"));
        }, TOKEN, ZONE);
    }

    private CloudflareCustomHostnameService build(ExchangeFunction exchange, String token, String zone) {
        CloudflareCustomHostnameService service = new CloudflareCustomHostnameService(
                WebClient.builder().exchangeFunction(exchange));
        ReflectionTestUtils.setField(service, "apiToken", token);
        ReflectionTestUtils.setField(service, "zoneId", zone);
        ReflectionTestUtils.setField(service, "ownDomains", new String[] {
                "modlix.com", "sitezump.ai", "authzump.ai", "adzump.ai", "leadzump.ai", "fincity.com" });
        return service;
    }

    private static final String OK = "{\"success\":true,\"errors\":[],\"result\":{\"id\":\"ch1\"}}";
    private static final String REFUSED = "{\"success\":false,\"errors\":[{\"code\":1409,\"message\":\"already exists\"}]}";
    private static final String FOUND = "{\"success\":true,\"errors\":[],\"result\":[{\"id\":\"ch1\",\"hostname\":\"rajaira.com\"}]}";
    private static final String NONE = "{\"success\":true,\"errors\":[],\"result\":[]}";

    // ---- configuration gate ------------------------------------------------

    @Test
    @DisplayName("unconfigured when either half is missing, which is the normal state on dev and stage")
    void configurationGate() {
        assertTrue(this.build(r -> Mono.empty(), TOKEN, ZONE).isConfigured());
        assertFalse(this.build(r -> Mono.empty(), "", ZONE).isConfigured());
        assertFalse(this.build(r -> Mono.empty(), TOKEN, "").isConfigured());
        assertFalse(this.build(r -> Mono.empty(), "", "").isConfigured());
    }

    // An unconfigured environment must not merely fail the call, it must never
    // make one. dev, stage and prod share the one modlix.com zone, so a request
    // from a developer's laptop would be a live entry against real traffic.
    @Test
    @DisplayName("makes no request at all when unconfigured")
    void silentWhenUnconfigured() {
        CloudflareCustomHostnameService service = this.build(request -> {
            this.requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(OK).build());
        }, "", "");

        StepVerifier.create(service.addHostname(HOST)).expectNext(Boolean.FALSE).verifyComplete();
        StepVerifier.create(service.removeHostname(HOST)).expectNext(Boolean.FALSE).verifyComplete();

        assertTrue(this.requests.isEmpty(), "an unconfigured environment must not reach Cloudflare");
    }

    // Every one of these is a real LIVE row in production. The four .ai and
    // fincity.com ones are the reason this list is not read from
    // security.subdomain.endings, which covers only modlix.com and sitezump.ai:
    // reusing it would have sent four of our own domains to Cloudflare as though
    // they belonged to a customer.
    @Test
    @DisplayName("never registers a domain we own, apex or subdomain")
    void skipsOwnDomains() {
        CloudflareCustomHostnameService service = this.serviceReplying(OK);

        for (String own : new String[] {
                "modlix.com", "appbuilder.modlix.com", "d0011223344556677.dev.modlix.com",
                "sitezump.ai", "dev.sitezump.ai",
                "authzump.ai", "adzump.ai", "leadzump.ai", "fincity.com" }) {

            assertTrue(service.isOwnDomain(own), own + " is ours");
            StepVerifier.create(service.addHostname(own)).expectNext(Boolean.FALSE).verifyComplete();
            StepVerifier.create(service.removeHostname(own)).expectNext(Boolean.FALSE).verifyComplete();
        }

        assertTrue(this.requests.isEmpty(), "our own domains must never reach Cloudflare");
    }

    // The counterpart: real customer domains from production must not be caught by
    // a suffix rule that is too greedy. "notfincity.com" is the classic false
    // positive for an endsWith check that forgets the dot.
    @Test
    @DisplayName("registers genuine customer domains, including near-misses on our own")
    void doesNotOverreach() {
        CloudflareCustomHostnameService service = this.serviceReplying(OK);

        for (String theirs : new String[] {
                "rajaira.com", "www.rajaira.com", "coevolvemistyshores.com",
                "purvasparklingspring.com", "terratones.in", "matrix24app.ca",
                "notfincity.com", "mymodlix.com" }) {

            assertFalse(service.isOwnDomain(theirs), theirs + " is a customer's");
        }

        StepVerifier.create(service.addHostname("www.rajaira.com")).expectNext(Boolean.TRUE).verifyComplete();
        assertEquals(1, this.requests.size());
    }

    @Test
    @DisplayName("makes no request for a blank hostname")
    void silentForBlankHostname() {
        CloudflareCustomHostnameService service = this.serviceReplying(OK);

        StepVerifier.create(service.addHostname("")).expectNext(Boolean.FALSE).verifyComplete();
        StepVerifier.create(service.addHostname(null)).expectNext(Boolean.FALSE).verifyComplete();

        assertTrue(this.requests.isEmpty());
    }

    // ---- add ---------------------------------------------------------------

    @Test
    @DisplayName("posts to the zone's custom_hostnames with the bearer token")
    void addPostsToTheRightPlace() {
        StepVerifier.create(this.serviceReplying(OK).addHostname(HOST))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        ClientRequest sent = this.requests.getFirst();
        assertEquals(HttpMethod.POST, sent.method());
        assertEquals("https://api.cloudflare.com/client/v4/zones/" + ZONE + "/custom_hostnames",
                sent.url().toString());
        assertEquals("Bearer " + TOKEN, sent.headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    // TXT rather than HTTP validation is the whole reason a live domain can be
    // migrated without a TLS-error window: the certificate is issued while the
    // domain still points at its old home.
    @Test
    @DisplayName("asks for TXT domain control validation, not HTTP")
    void addUsesTxtValidation() {
        CloudflareCustomHostnameService service = this.build(request -> {
            this.requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(OK).build());
        }, TOKEN, ZONE);

        StepVerifier.create(service.addHostname(HOST)).expectNext(Boolean.TRUE).verifyComplete();

        String body = bodyOf(this.requests.getFirst());
        assertTrue(body.contains(HOST), "body should name the hostname: " + body);
        assertTrue(body.contains("txt"), "body should ask for txt validation: " + body);
        assertFalse(body.contains("\"method\":\"http\""), "http validation would need DNS moved first");
    }

    // Two rows for the same registrable domain are two separate registrations, and
    // have to be: the platform resolves by exact host equality
    // (ClientUrlPattern.isValidClientURLPattern) and so does Cloudflare, so an
    // apex certificate does not cover www and vice versa. Production bears this
    // out -- most customer domains already carry exactly two LIVE rows.
    @Test
    @DisplayName("apex and www are two registrations, and the scheme is not part of either")
    void apexAndWwwAreSeparate() {
        CloudflareCustomHostnameService service = this.serviceReplying(OK);

        StepVerifier.create(service.addHostname("abxxx.com")).expectNext(Boolean.TRUE).verifyComplete();
        StepVerifier.create(service.addHostname("www.abxxx.com")).expectNext(Boolean.TRUE).verifyComplete();

        assertEquals(2, this.requests.size(), "one call per row, never inferred from the other");
        assertTrue(bodyOf(this.requests.get(0)).contains("\"abxxx.com\""));
        assertTrue(bodyOf(this.requests.get(1)).contains("\"www.abxxx.com\""));
    }

    @Test
    @DisplayName("false, not an error, when Cloudflare refuses")
    void addSurvivesRefusal() {
        StepVerifier.create(this.serviceReplying(REFUSED).addHostname(HOST))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    // The caller records the customer's domain regardless. A transport failure
    // here must never propagate into ClientUrlService and fail that write.
    @Test
    @DisplayName("false, not an error, when the call cannot be made at all")
    void addSurvivesTransportFailure() {
        StepVerifier.create(this.serviceFailing().addHostname(HOST))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    // ---- remove ------------------------------------------------------------

    @Test
    @DisplayName("looks the hostname up, then deletes by the id it found")
    void removeFindsThenDeletes() {
        StepVerifier.create(this.serviceReplying(FOUND, OK).removeHostname(HOST))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertEquals(2, this.requests.size());

        ClientRequest lookup = this.requests.get(0);
        assertEquals(HttpMethod.GET, lookup.method());
        assertTrue(lookup.url().toString().endsWith("/custom_hostnames?hostname=" + HOST));

        ClientRequest delete = this.requests.get(1);
        assertEquals(HttpMethod.DELETE, delete.method());
        assertTrue(delete.url().toString().endsWith("/custom_hostnames/ch1"));
    }

    // Every row created before this class existed is in this state, as is every
    // row from a period when the token was absent. Not finding one is ordinary.
    @Test
    @DisplayName("false and no delete when the hostname was never registered")
    void removeIsQuietWhenNotFound() {
        StepVerifier.create(this.serviceReplying(NONE).removeHostname(HOST))
                .expectNext(Boolean.FALSE)
                .verifyComplete();

        assertEquals(1, this.requests.size(), "nothing to delete, so no delete should be attempted");
    }

    @Test
    @DisplayName("false, not an error, when the lookup itself fails")
    void removeSurvivesTransportFailure() {
        StepVerifier.create(this.serviceFailing().removeHostname(HOST))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    /**
     * The rendered request body.
     *
     * A {@link ClientRequest} holds a BodyInserter rather than bytes, so the only
     * way to see what would actually go on the wire is to write it into something.
     */
    private static String bodyOf(ClientRequest request) {
        MockClientHttpRequest rendered = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(rendered, ExchangeStrategies.withDefaults()).block();
        return rendered.getBodyAsString().block();
    }
}
