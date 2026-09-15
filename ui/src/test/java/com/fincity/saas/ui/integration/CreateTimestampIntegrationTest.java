package com.fincity.saas.ui.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

/**
 * What a never-updated document looks like.
 *
 * <p>The answer is not ours to pick: the MySQL side of this platform already settled it. There
 * {@code UPDATED_AT} is {@code NOT NULL DEFAULT CURRENT_TIMESTAMP}, so a fresh row carries
 * updatedAt equal to createdAt, while {@code UPDATED_BY} is {@code DEFAULT NULL} and nothing copies
 * createdBy into it. Checked against local security_client, security_user and security_app: not one
 * null UPDATED_AT in 9668 rows, and UPDATED_BY null on every row never updated since.
 *
 * <p>Mongo had no equivalent, because both fields arrive on the wire like any other and nothing
 * cleared them. That is invisible for an ordinary create, where no client sends them. It was very
 * visible for a transport, which sends the whole document: an object created by a promotion kept
 * the SOURCE environment's pair, so updatedAt could predate the createdAt stamped a moment later,
 * and updatedBy pointed into a different environment's user table. Seen on stage after a sitezump
 * promotion, where core.storage/blogs read createdAt 2026-09-14 and updatedAt 2026-09-05.
 *
 * <p>The second test is the one that matters. The first would have passed before the fix too,
 * since nothing was sending these fields.
 */
@DisplayName("Timestamps on a freshly created document")
class CreateTimestampIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE_NAME = "timestampPage";

    @Autowired
    private PageService pageService;

    private Page newPage(String name) {
        Page page = new Page();
        page.setName(name).setAppCode(APP_CODE).setClientCode(SYSTEM);
        return page;
    }

    private <T> T asSystem(Mono<T> mono) {
        ContextAuthentication ca = this.authFor(SYSTEM, allAuthoritiesFor("Page"));
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)).block();
    }

    @Test
    @Timeout(30)
    @DisplayName("updatedAt equals createdAt, and updatedBy is null")
    void createStampsUpdatedAtToMatchCreatedAt() {

        Page created = asSystem(this.pageService.create(newPage(PAGE_NAME)));

        assertNotNull(created);
        assertNotNull(created.getCreatedAt());

        // The same instant, not merely close: both come from one variable, the way a
        // single MySQL CURRENT_TIMESTAMP default makes them agree. Two now() calls
        // would land microseconds apart and read as "updated just after creation".
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());

        assertNull(created.getUpdatedBy());
    }

    @Test
    @Timeout(30)
    @DisplayName("a create carrying another environment's updatedAt does not keep it")
    void createIgnoresCallerSuppliedUpdateStamps() {

        // Exactly the shape a transport apply hands to create: a whole document
        // including the source environment's audit fields.
        Page incoming = newPage(PAGE_NAME + "FromTransport");
        LocalDateTime foreign = LocalDateTime.of(2020, 1, 1, 0, 0);
        incoming.setUpdatedAt(foreign);
        incoming.setUpdatedBy("someone-on-another-environment");

        Page created = asSystem(this.pageService.create(incoming));

        assertNotNull(created);
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
        assertNull(created.getUpdatedBy());

        // The specific failure this guards: updatedAt older than createdAt.
        assertNotEquals(foreign, created.getUpdatedAt());
    }
}
