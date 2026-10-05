package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Mono;

/**
 * What a storage is refused for, and how clearly.
 *
 * Saving is where the whole fleet finds out: one definition fans out to every
 * tenant of the app, so a problem that is only noticed when a table is built has
 * already been noticed by the first client and missed by the rest.
 */
@DisplayName("Storage validation at save")
class StorageValidationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StorageService storageService;

    private <T> T asClient(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Storage"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    private static Storage storageWith(Map<String, Object> properties) {

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);

        Storage storage = new Storage();
        storage.setName("probe").setAppCode(APP_CODE).setClientCode(SYSTEM).setVersion(1);
        storage.setUniqueName("testapp_system_probe");
        storage.setSchema(schema);
        return storage;
    }

    private static Map<String, Object> field(String type) {
        Map<String, Object> property = new HashMap<>();
        property.put("type", type);

        Map<String, Object> properties = new HashMap<>();
        properties.put("price", property);
        return properties;
    }

    @Test
    @Timeout(300)
    @DisplayName("an unrecognised schema type is a bad request that names it, not a server error")
    void unknownTypeIsNamed() {
        // The type adapter throws IllegalArgumentException on a type it does not
        // know, and nothing caught it, so the author got a 500 carrying an enum
        // constant name and no indication of which field was wrong.
        GenericException e = assertThrows(
                GenericException.class, () -> asClient(this.storageService.create(storageWith(field("NOTATYPE")))));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(String.valueOf(e.getMessage()).contains("NOTATYPE"), String.valueOf(e.getMessage()));
    }

    @Test
    @Timeout(300)
    @DisplayName("NUMBER is accepted, because KIRun maps it to DOUBLE on purpose")
    void numberIsAnAlias() {
        // Worth pinning: NUMBER is not a SchemaType, and it would be easy to read
        // that as a silent fallback and "fix" it. The type adapter aliases it
        // deliberately, and 19 live fields rely on that.
        assertNotNull(asClient(this.storageService.create(storageWith(field("NUMBER")))));
    }

    @Test
    @Timeout(300)
    @DisplayName("an ordinary type still saves")
    void ordinaryTypeSaves() {
        assertNotNull(asClient(this.storageService.create(storageWith(field("STRING")))));
    }
}
