package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.ui.service.PageService;

/**
 * The `ui` service has never had a Spring context in a test. This proves the
 * harness itself works before anything is built on it.
 */
@DisplayName("ui integration harness")
class ContextLoadsTest extends AbstractIntegrationTest {

    @Autowired
    private PageService pageService;

    @Test
    @DisplayName("the Spring context starts and Mongo is reachable")
    void contextLoads() {

        assertNotNull(this.pageService);
        assertNotNull(this.mongoTemplate);

        // Round-trip Mongo to prove the container is wired, not just that beans exist.
        Long count = this.mongoTemplate.getCollectionNames().count().block();
        assertNotNull(count);

        assertTrue(mongo.isRunning());
    }
}
