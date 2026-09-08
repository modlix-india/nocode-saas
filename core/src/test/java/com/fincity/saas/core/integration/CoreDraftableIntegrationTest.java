package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.service.CorePublishService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.mongo.document.Draft;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Mono;

/**
 * Core objects draft and publish like ui ones.
 *
 * They did not until now, and the gap mattered more than it sounds: a page's
 * draft usually depends on a storage whose schema moved with it, or a connection,
 * template or event. With only ui draftable, a change spanning both had to be
 * published to be seen at all, which is the thing the draft surface exists to
 * avoid.
 *
 * The routes were always there, inherited from AbstractOverridableDataController,
 * and the core DraftService bean already existed unused. All that was missing was
 * the flag, which is why the interesting assertions here are about the plumbing
 * behind it working end to end rather than about the flag itself.
 */
@DisplayName("Core objects are draftable")
class CoreDraftableIntegrationTest extends AbstractIntegrationTest {

    private static final String STORAGE_NAME = "draftableStorage";

    @Autowired
    private StorageService storageService;

    @Autowired
    private CorePublishService publishService;

    @Autowired
    private ApplicationContext applicationContext;

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private Storage storedStorage() {

        Storage storage = new Storage();
        storage.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "Draftable");
        schema.put("type", "OBJECT");
        Map<String, Object> properties = new HashMap<>();
        properties.put("title", Map.of("type", "STRING"));
        schema.put("properties", properties);

        storage.setSchema(schema);
        storage.setUniqueName("testapp_system_draftablestorage");
        return this.insertRaw(storage);
    }

    private Storage draftEdit(Storage stored, String fieldName) {

        Storage edit = new Storage();
        edit.setId(stored.getId());
        edit.setName(STORAGE_NAME).setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(stored.getVersion());

        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "Draftable");
        schema.put("type", "OBJECT");
        schema.put("properties", Map.of(fieldName, Map.of("type", "STRING")));
        edit.setSchema(schema);
        edit.setUniqueName(stored.getUniqueName());
        return edit;
    }

    @Test
    @Timeout(60)
    @DisplayName("a storage draft saves without touching the live document")
    void draftDoesNotTouchLive() {

        setInheritance(List.of(SYSTEM));
        Storage stored = storedStorage();

        assertNotNull(asClient(this.storageService.saveDraft(draftEdit(stored, "draftedField"))));

        List<Draft> drafts = this.mongoTemplate.findAll(Draft.class).collectList().block();
        assertEquals(1, drafts.size());
        assertEquals("STORAGE", drafts.get(0).getObjectType());

        // The live schema is untouched, which is the whole point of a draft.
        Storage live = this.mongoTemplate.findAll(Storage.class).blockFirst();
        assertNotNull(live);
        @SuppressWarnings("unchecked")
        Map<String, Object> liveProps = (Map<String, Object>) live.getSchema().get("properties");
        assertTrue(liveProps.containsKey("title"), "the draft leaked into the live definition");
        assertFalse(liveProps.containsKey("draftedField"));
    }

    @Test
    @Timeout(60)
    @DisplayName("the draft read returns the draft, the live read does not")
    void draftReadIsSeparate() {

        setInheritance(List.of(SYSTEM));
        Storage stored = storedStorage();
        asClient(this.storageService.saveDraft(draftEdit(stored, "draftedField")));

        Storage drafted = asClient(this.storageService.readDraft(stored.getId()));
        @SuppressWarnings("unchecked")
        Map<String, Object> draftProps = (Map<String, Object>) drafted.getSchema().get("properties");
        assertTrue(draftProps.containsKey("draftedField"));

        Storage plain = asClient(this.storageService.read(stored.getId()));
        @SuppressWarnings("unchecked")
        Map<String, Object> plainProps = (Map<String, Object>) plain.getSchema().get("properties");
        assertTrue(plainProps.containsKey("title"));
        assertFalse(plainProps.containsKey("draftedField"));
    }

    @Test
    @Timeout(60)
    @DisplayName("core has its own pending list and app-level publish")
    void corePendingAndPublishAll() {

        setInheritance(List.of(SYSTEM));
        Storage stored = storedStorage();
        asClient(this.storageService.saveDraft(draftEdit(stored, "draftedField")));

        Map<String, List<Map<String, Object>>> pending = asClient(this.publishService.pending(APP_CODE, SYSTEM));
        assertEquals(1, pending.size());
        assertTrue(pending.containsKey("STORAGE"), "core pending did not group the draft by its type: " + pending);

        Map<String, Object> result = asClient(this.publishService.publishAll(APP_CODE, SYSTEM));
        assertEquals(1, result.get("attempted"));
        assertEquals(1L, result.get("published"), "publishAll reported: " + result.get("results"));

        // Published means promoted and the draft row gone, the same contract as ui.
        assertTrue(this.mongoTemplate.findAll(Draft.class).collectList().block().isEmpty());

        Storage live = this.mongoTemplate.findAll(Storage.class).blockFirst();
        @SuppressWarnings("unchecked")
        Map<String, Object> liveProps = (Map<String, Object>) live.getSchema().get("properties");
        assertTrue(liveProps.containsKey("draftedField"), "publish did not promote the draft");
    }

    /**
     * Every overridable core service has to be in CorePublishService's list.
     *
     * A missing one fails silently and invisibly: its drafts never appear in
     * `pending` and `publishAll` never ships them, so they sit unpublished with
     * nothing anywhere saying why. Walking the context is the only way to catch a
     * service added later and not registered.
     */
    @Test
    @Timeout(60)
    @DisplayName("every draftable core service is registered for publishing")
    void publishListIsComplete() throws Exception {

        java.lang.reflect.Field field = com.fincity.saas.commons.mongo.service.AbstractPublishService.class
                .getDeclaredField("servicesByObjectType");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> registered = (Map<String, ?>) field.get(this.publishService);

        // isDraftable() is protected, and widening it just for a test would be the
        // wrong trade: reflection here keeps the production surface honest.
        java.lang.reflect.Method isDraftable = AbstractOverridableDataService.class
                .getDeclaredMethod("isDraftable");
        isDraftable.setAccessible(true);

        List<String> missing = new java.util.ArrayList<>();
        List<String> draftable = new java.util.ArrayList<>();
        for (AbstractOverridableDataService<?, ?> service : this.applicationContext
                .getBeansOfType(AbstractOverridableDataService.class).values()) {

            if (!Boolean.TRUE.equals(isDraftable.invoke(service)))
                continue;

            String type = service.getObjectName().toUpperCase();
            draftable.add(type);
            if (!registered.containsKey(type))
                missing.add(type);
        }
        java.util.Collections.sort(missing);

        // Without this the test passes when the context scan finds nothing at all,
        // which is the one way it could go green while telling you nothing.
        assertTrue(draftable.contains("STORAGE") && draftable.size() >= 11,
                "the context scan did not find the draftable core services, so this test proved nothing: "
                        + draftable);

        assertTrue(missing.isEmpty(),
                "draftable core services with no publish registration, so their drafts can never be shipped: "
                        + missing);
    }
}
