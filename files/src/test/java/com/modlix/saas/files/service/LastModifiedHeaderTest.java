package com.modlix.saas.files.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * FileDetail.lastModifiedTime is epoch SECONDS. Date headers are epoch MILLIS. Passing one as the
 * other sent "Wed, 21 Jan 1970 ..." as Last-Modified for a file written in 2026.
 */
class LastModifiedHeaderTest {

    private static final long WRITTEN_AT_SECONDS = ZonedDateTime.of(2026, 10, 4, 16, 34, 38, 0, ZoneOffset.UTC)
            .toEpochSecond();

    @Test
    void lastModifiedHeaderCarriesTheRealDate() {

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setDateHeader("Last-Modified",
                AbstractFilesResourceService.lastModifiedHeaderMillis(WRITTEN_AT_SECONDS));

        assertEquals("Sun, 04 Oct 2026 16:34:38 GMT", response.getHeader("Last-Modified"));
    }

    @Test
    void ifModifiedSinceIsReadAsAnHttpDateInSeconds() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME
                .format(Instant.ofEpochSecond(WRITTEN_AT_SECONDS).atZone(ZoneOffset.UTC)));

        assertEquals(WRITTEN_AT_SECONDS, AbstractFilesResourceService.ifModifiedSinceSeconds(request));
    }

    @Test
    void anAbsentOrUnreadableIfModifiedSinceIsMinusOne() {

        assertEquals(-1L, AbstractFilesResourceService.ifModifiedSinceSeconds(new MockHttpServletRequest()));

        MockHttpServletRequest garbage = new MockHttpServletRequest();
        garbage.addHeader("If-Modified-Since", "not a date");
        assertEquals(-1L, AbstractFilesResourceService.ifModifiedSinceSeconds(garbage));
    }
}
