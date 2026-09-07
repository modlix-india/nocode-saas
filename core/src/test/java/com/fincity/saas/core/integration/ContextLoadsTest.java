package com.fincity.saas.core.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.commons.core.service.StorageService;

/**
 * Proves the `core` harness itself works before anything is built on it. The
 * `core` service has never had a Spring context in a test.
 */
@DisplayName("core integration harness")
class ContextLoadsTest extends AbstractIntegrationTest {

    @Autowired
    private StorageService storageService;

    @Test
    @DisplayName("the Spring context starts and Mongo is reachable")
    void contextLoads() {

        assertNotNull(this.storageService);
        assertNotNull(this.mongoTemplate);

        Long count = this.mongoTemplate.getCollectionNames().count().block();
        assertNotNull(count);

        assertTrue(mongo.isRunning());
    }
}
