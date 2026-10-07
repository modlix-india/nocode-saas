package com.fincity.saas.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fincity.saas.commons.model.ObjectWithUniqueID;

/**
 * The three Cache-Control policies the engine routes hand out. Page and application
 * definitions depend on the login, so they revalidate on every use (QA-0125: a
 * page stayed open for a week after the token was cleared); style, theme and the
 * static routes keep their freshness lifetime; drafts are never stored.
 */
class ResponseEntityUtilsTest {

    private static final ObjectWithUniqueID<String> OBJ = new ObjectWithUniqueID<>("body", "abc123");

    @Test
    @DisplayName("revalidate: 200 carries the ETag and no-cache, with no freshness lifetime")
    void revalidateOk() {

        ResponseEntity<String> r = ResponseEntityUtils.makeRevalidateResponseEntity(OBJ, null).block();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("W/abc123", r.getHeaders().getFirst("ETag"));
        assertEquals("no-cache", r.getHeaders().getFirst("Cache-Control"));
        assertEquals("body", r.getBody());
    }

    @Test
    @DisplayName("revalidate: a matching If-None-Match is still a 304, and the 304 carries no-cache")
    void revalidateNotModified() {

        ResponseEntity<String> r = ResponseEntityUtils.makeRevalidateResponseEntity(OBJ, "W/abc123").block();

        assertEquals(HttpStatus.NOT_MODIFIED, r.getStatusCode());
        assertNull(r.getBody());
        // Without it a browser holding the old seven day entry would keep that
        // lifetime after each 304.
        assertEquals("no-cache", r.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    @DisplayName("revalidate: a different ETag gets the new body")
    void revalidateChanged() {

        ResponseEntity<String> r = ResponseEntityUtils.makeRevalidateResponseEntity(OBJ, "W/zzz999").block();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("body", r.getBody());
    }

    @Test
    @DisplayName("the other resources keep their lifetime")
    void lifetimeUnchanged() {

        ResponseEntity<String> r = ResponseEntityUtils.makeResponseEntity(OBJ, null, 604800).block();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("max-age=604800, must-revalidate", r.getHeaders().getFirst("Cache-Control"));

        ResponseEntity<String> nm = ResponseEntityUtils.makeResponseEntity(OBJ, "W/abc123", 604800).block();
        assertEquals(HttpStatus.NOT_MODIFIED, nm.getStatusCode());
        assertEquals("max-age=604800, must-revalidate", nm.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    @DisplayName("drafts stay no-store")
    void draftNoStore() {

        ResponseEntity<String> r = ResponseEntityUtils.makeDraftResponseEntity(OBJ, null).block();

        assertEquals("no-store", r.getHeaders().getFirst("Cache-Control"));
    }

    @Test
    @DisplayName("a content type is still applied")
    void contentType() {

        ResponseEntity<String> r = ResponseEntityUtils.makeResponseEntity(OBJ, null, 10, "text/html").block();

        assertEquals("text/html", r.getHeaders().getContentType().toString());
    }
}
