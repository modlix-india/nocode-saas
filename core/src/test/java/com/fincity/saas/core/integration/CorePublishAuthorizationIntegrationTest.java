package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.service.CorePublishService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Mono;

/**
 * Authorization on the core app-level publish routes.
 *
 * The gate is `AbstractPublishService.authorizedClientCode`, shared with ui. It is
 * tested on both sides rather than once, because the two modules sit behind
 * different HTTP-layer postures and a change to either could silently remove a
 * layer: `ui` passes `"/**"` as its permitAll exclusion list, so every ui route is
 * anonymous at the filter and the service is the only gate, while `api/core/publish`
 * is not in core's exclusion list and is authenticated at the filter as well.
 *
 * Core is therefore the stricter of the two today. These tests pin the service
 * check independently of that, so core stays safe if its filter list ever grows a
 * wildcard.
 */
@DisplayName("Core publish authorization")
class CorePublishAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String OTHER_CLIENT = "OTHERCL";
    private static final String STORAGE_NAME = "authzStorage";

    @Autowired
    private CorePublishService publishService;

    @Autowired
    private StorageService storageService;

    private ContextAuthentication authenticated(String clientCode) {
        return this.authFor(clientCode, allAuthoritiesFor("Storage"));
    }

    private <T> T as(Mono<T> mono, ContextAuthentication ca) {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private void seedDraft() {

        setInheritance(List.of(SYSTEM));

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "Authz");
        schema.put("type", "OBJECT");
        storage.setSchema(schema);
        storage.setUniqueName("testapp_system_authzstorage");
        Storage stored = this.insertRaw(storage);

        Storage edit = new Storage();
        edit.setId(stored.getId());
        edit.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        Map<String, Object> edited = new HashMap<>();
        edited.put("name", "Authz");
        edited.put("type", "OBJECT");
        edited.put("properties", Map.of("drafted", Map.of("type", "STRING")));
        edit.setSchema(edited);
        edit.setUniqueName(stored.getUniqueName());

        assertNotNull(as(this.storageService.saveDraft(edit), authenticated(SYSTEM)));
    }

    @Test
    @Timeout(60)
    @DisplayName("pending is refused without authentication")
    void pendingRefusesAnonymous() {

        seedDraft();

        // FORBIDDEN specifically, not just "some exception". An anonymous caller
        // hitting an NPE would also throw, and would also look like a pass here
        // while actually being a 500 and a stack trace in the log.
        GenericException thrown = assertThrows(GenericException.class,
                () -> this.publishService.pending(APP_CODE, SYSTEM).block(),
                "the core pending list was readable with no authentication at all");
        assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode(),
                "anonymous access was refused, but not cleanly: " + thrown.getMessage());
    }

    @Test
    @Timeout(60)
    @DisplayName("publishAll is refused without authentication")
    void publishAllRefusesAnonymous() {

        seedDraft();

        GenericException thrown = assertThrows(GenericException.class,
                () -> this.publishService.publishAll(APP_CODE, SYSTEM).block(),
                "core drafts could be shipped with no authentication at all");
        assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode(),
                "anonymous access was refused, but not cleanly: " + thrown.getMessage());

        // And nothing was published on the way to being refused.
        assertFalse(this.mongoTemplate.findAll(com.fincity.saas.commons.mongo.document.Draft.class)
                .collectList().block().isEmpty(), "a refused publishAll still shipped the draft");
    }

    @Test
    @Timeout(60)
    @DisplayName("a clientCode the caller does not manage is refused")
    void unmanagedClientCodeRefused() {

        seedDraft();

        // A supplied clientCode is a claim, not a fact. A non-system caller who does
        // not manage it must not be able to read another tenant's pending work,
        // which is where the objectIds that make draft hijacking possible come from.
        Mockito.when(this.feignAuthenticationService.doesClientManageClientCode(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Mono.just(Boolean.FALSE));

        ContextAuthentication caller = this.authFor(OTHER_CLIENT, allAuthoritiesFor("Storage"));

        assertThrows(Exception.class, () -> as(this.publishService.pending(APP_CODE, SYSTEM), caller),
                "a caller read the pending list of a client it does not manage");
    }

    @Test
    @Timeout(60)
    @DisplayName("listing needs read access, publishing needs write")
    void readVersusWrite() {

        seedDraft();

        // Read but not write: the pending list is allowed, shipping is not.
        Mockito.when(this.feignAuthenticationService.hasWriteAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        ContextAuthentication caller = authenticated(SYSTEM);

        Map<String, List<Map<String, Object>>> pending = as(this.publishService.pending(APP_CODE, SYSTEM), caller);
        assertEquals(1, pending.size(), "read access should be enough to list pending work");

        assertThrows(Exception.class, () -> as(this.publishService.publishAll(APP_CODE, SYSTEM), caller),
                "publishing went ahead on read access alone");
    }

    @Test
    @Timeout(60)
    @DisplayName("a refused call discloses no objectId")
    void refusalDisclosesNothing() {

        seedDraft();

        Mockito.when(this.feignAuthenticationService.hasReadAccess(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));

        Exception thrown = assertThrows(Exception.class,
                () -> as(this.publishService.pending(APP_CODE, SYSTEM), authenticated(SYSTEM)));

        String message = String.valueOf(thrown.getMessage());
        assertFalse(message.contains("objectId"), "the refusal leaked draft metadata: " + message);
        assertFalse(message.contains(STORAGE_NAME),
                "the refusal named an object the caller may not see: " + message);
    }

    @Test
    @Timeout(60)
    @DisplayName("an authorized caller can list and publish")
    void authorizedCallerSucceeds() {

        seedDraft();

        ContextAuthentication caller = authenticated(SYSTEM);

        assertEquals(1, as(this.publishService.pending(APP_CODE, SYSTEM), caller).size());

        Map<String, Object> result = as(this.publishService.publishAll(APP_CODE, SYSTEM), caller);
        assertEquals(1, result.get("attempted"));
        assertEquals(1L, result.get("published"), "results: " + result.get("results"));
    }

    @Test
    @Timeout(60)
    @DisplayName("no clientCode falls back to the caller's own, never to everyone's")
    void noClientCodeResolvesFromContext() {

        seedDraft();

        Map<String, List<Map<String, Object>>> pending = as(this.publishService.pending(APP_CODE, null),
                authenticated(SYSTEM));

        assertTrue(pending.containsKey("STORAGE"));
        assertEquals(SYSTEM, pending.get("STORAGE").get(0).get("clientCode"));
    }
}
