package com.fincity.saas.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;

import reactor.core.publisher.Mono;

/**
 * The two decisions this service makes on every query, tested without a server.
 *
 * Both are the kind that fail silently: a site computed with the wrong case reads an empty
 * partition rather than erroring, and a timezone resolved from the wrong place produces
 * numbers that are merely different rather than obviously broken.
 */
class AnalyticsServiceTest {

    private static AnalyticsService serviceWithClientZone(String zone) {
        FeignAuthenticationService security = mock(FeignAuthenticationService.class);

        Client client = new Client();
        client.setCode("SYSTEM");
        client.setTimeZone(zone);
        when(security.getClientByCode(anyString())).thenReturn(zone == null ? Mono.empty() : Mono.just(client));

        return new AnalyticsService(null, null, null, null, security);
    }

    @SuppressWarnings("unchecked")
    private static String resolve(AnalyticsService service, Map<String, Object> body) throws Exception {
        Method m = AnalyticsService.class.getDeclaredMethod("resolveTimeZone", String.class, Map.class);
        m.setAccessible(true);
        return ((Mono<String>) m.invoke(service, "SYSTEM", body)).block();
    }

    @Test
    void theRequestsOwnZoneWinsOverTheClients() throws Exception {
        AnalyticsService service = serviceWithClientZone("Asia/Kolkata");
        assertEquals("Europe/London", resolve(service, Map.of("timezone", "Europe/London")));
    }

    @Test
    void theClientsZoneIsUsedWhenTheRequestNamesNone() throws Exception {
        AnalyticsService service = serviceWithClientZone("America/New_York");
        assertEquals("America/New_York", resolve(service, new LinkedHashMap<>()));
    }

    /**
     * Not UTC. The engine's own default is UTC precisely because it must not inherit an
     * opinion from whichever host it runs on; this side does have one, and it is the same
     * value security_client.TIME_ZONE already defaults to, so the two cannot disagree.
     */
    @Test
    void thePlatformDefaultIsUsedWhenTheClientHasNoZone() throws Exception {
        assertEquals("Asia/Kolkata", resolve(serviceWithClientZone(null), new LinkedHashMap<>()));
        assertEquals("Asia/Kolkata", resolve(serviceWithClientZone(""), new LinkedHashMap<>()));
    }

    /**
     * A zone nobody asked for, on a record nobody can fix from here, must not take the
     * dashboard down — but one that WAS asked for is a 400, because quietly answering in a
     * different zone from the one requested produces a number nobody can reconcile.
     */
    @Test
    void anUnusableZoneOnTheClientRecordFallsBackRatherThanFailing() throws Exception {
        assertEquals("Asia/Kolkata", resolve(serviceWithClientZone("Mars/Olympus"), new LinkedHashMap<>()));
    }

    @Test
    void theSiteKeyIsCanonicalAndMatchesTheEngine() {
        assertEquals("modlix_SYSTEM", AnalyticsService.siteKey("modlix", "SYSTEM"));
        assertEquals("modlix_SYSTEM", AnalyticsService.siteKey("Modlix", "system"));
        assertEquals("theorem_THRM", AnalyticsService.siteKey("THEOREM", "thrm"));
    }

    /**
     * The site is overwritten, not validated. A caller who could name their own site could
     * read another tenant's numbers, and a check that has to be right every time is worse
     * than an assignment that cannot be wrong.
     */
    @Test
    void theCallersOwnSiteIsOverwritten() throws Exception {
        AnalyticsService service = serviceWithClientZone("Asia/Kolkata");

        Method m = AnalyticsService.class.getDeclaredMethod("engineRequest", Map.class, String.class,
                String.class, String.class);
        m.setAccessible(true);

        Map<String, Object> caller = new LinkedHashMap<>();
        caller.put("widget", "topPages");
        caller.put("site", "someoneelse_OTHER");
        caller.put("timezone", "Europe/London");

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) m.invoke(service, caller, "modlix", "SYSTEM",
                "Asia/Kolkata");

        assertEquals("modlix_SYSTEM", sent.get("site"));
        assertEquals("Asia/Kolkata", sent.get("timezone"));
        assertEquals("topPages", sent.get("widget"));
    }
}
