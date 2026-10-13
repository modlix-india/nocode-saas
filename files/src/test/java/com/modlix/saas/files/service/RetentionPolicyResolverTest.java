package com.modlix.saas.files.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.modlix.saas.files.dto.FilesRetentionPolicy;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyAppliesTo;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyLevel;
import com.modlix.saas.files.jooq.enums.FilesRetentionPolicyResourceType;
import com.modlix.saas.files.service.RetentionPolicyResolver.Resolved;

/** Prefix matching and the order in which rows win: own, nearest manager, app. */
class RetentionPolicyResolverTest {

    private static final String PREFIX = "whatsapp/incoming";

    static FilesRetentionPolicy appRow(String path, Integer days) {
        return new FilesRetentionPolicy().setAppCode("app").setClientCode("OWNER")
                .setResourceType(FilesRetentionPolicyResourceType.SECURED).setPath(path).setRetentionDays(days)
                .setAppliesTo(FilesRetentionPolicyAppliesTo.SELF).setLevel(FilesRetentionPolicyLevel.APP);
    }

    static FilesRetentionPolicy clientRow(String path, String client, FilesRetentionPolicyAppliesTo to,
            Integer days) {
        return new FilesRetentionPolicy().setAppCode("app").setClientCode(client)
                .setResourceType(FilesRetentionPolicyResourceType.SECURED).setPath(path).setRetentionDays(days)
                .setAppliesTo(to).setLevel(FilesRetentionPolicyLevel.CLIENT);
    }

    @Test
    void prefixMatchesOnSegmentBoundariesOnly() {
        List<String> registered = List.of("whatsapp/in");

        assertNull(RetentionPolicyResolver.longestPrefix(registered, "whatsapp/incoming/a.png"));
        assertEquals("whatsapp/in", RetentionPolicyResolver.longestPrefix(registered, "whatsapp/in/a.png"));
        assertEquals("whatsapp/in", RetentionPolicyResolver.longestPrefix(registered, "whatsapp/in"));
        assertNull(RetentionPolicyResolver.longestPrefix(registered, "whatsapp"));
        assertNull(RetentionPolicyResolver.longestPrefix(registered, "other/whatsapp/in/a.png"));
    }

    @Test
    void longestPrefixWinsAndEmptyMatchesEverything() {
        List<String> registered = List.of("", "whatsapp", "whatsapp/incoming");

        assertEquals("whatsapp/incoming",
                RetentionPolicyResolver.longestPrefix(registered, "/whatsapp/incoming/x/y.jpg"));
        assertEquals("whatsapp", RetentionPolicyResolver.longestPrefix(registered, "whatsapp/outgoing/z.jpg"));
        assertEquals("", RetentionPolicyResolver.longestPrefix(registered, "docs/readme.pdf"));
    }

    @Test
    void nestedPrefixesAreTheLongerOnesInside() {
        List<String> registered = List.of("", "whatsapp", "whatsapp/incoming", "whatsappx");

        assertEquals(List.of("whatsapp", "whatsapp/incoming", "whatsappx"),
                RetentionPolicyResolver.nestedPrefixes(registered, ""));
        assertEquals(List.of("whatsapp/incoming"), RetentionPolicyResolver.nestedPrefixes(registered, "whatsapp"));
        assertTrue(RetentionPolicyResolver.nestedPrefixes(registered, "whatsapp/incoming").isEmpty());
    }

    @Test
    void normalizeStripsSlashesAndRefusesDotSegments() {
        assertEquals("a/b", RetentionPolicyResolver.normalizePath(" /a//b/ "));
        assertEquals("", RetentionPolicyResolver.normalizePath("/"));
        assertEquals("", RetentionPolicyResolver.normalizePath(null));
        assertThrows(IllegalArgumentException.class, () -> RetentionPolicyResolver.normalizePath("a/../b"));
        assertThrows(IllegalArgumentException.class, () -> RetentionPolicyResolver.normalizePath("./a"));
    }

    @Test
    void appRowAppliesWhenNothingOverrides() {
        Resolved r = RetentionPolicyResolver.resolve(PREFIX, List.of(appRow(PREFIX, 30)), List.of("C", "M1", "SYSTEM"));

        assertEquals(30, r.days());
        assertEquals(RetentionPolicyResolver.SOURCE_APP, r.source());
    }

    @Test
    void ownSelfRowBeatsManagersAndApp() {
        List<FilesRetentionPolicy> rows = List.of(appRow(PREFIX, 30),
                clientRow(PREFIX, "M1", FilesRetentionPolicyAppliesTo.MANAGED, 60),
                clientRow(PREFIX, "C", FilesRetentionPolicyAppliesTo.SELF, 7));

        Resolved r = RetentionPolicyResolver.resolve(PREFIX, rows, List.of("C", "M1", "SYSTEM"));

        assertEquals(7, r.days());
        assertEquals(RetentionPolicyResolver.SOURCE_OWN, r.source());
        assertEquals(30, r.appDays());
    }

    @Test
    void nearestManagerManagedRowBeatsFartherOnesAndApp() {
        List<FilesRetentionPolicy> rows = List.of(appRow(PREFIX, 30),
                clientRow(PREFIX, "SYSTEM", FilesRetentionPolicyAppliesTo.MANAGED, 90),
                clientRow(PREFIX, "M2", FilesRetentionPolicyAppliesTo.MANAGED, 60),
                // M1's SELF row is about M1 itself, not the clients it manages.
                clientRow(PREFIX, "M1", FilesRetentionPolicyAppliesTo.SELF, 1),
                // C's MANAGED row is about C's own managed clients, not C.
                clientRow(PREFIX, "C", FilesRetentionPolicyAppliesTo.MANAGED, 2));

        Resolved r = RetentionPolicyResolver.resolve(PREFIX, rows, List.of("C", "M1", "M2", "SYSTEM"));

        assertEquals(60, r.days());
        assertEquals(RetentionPolicyResolver.SOURCE_MANAGER_PREFIX + "M2", r.source());
    }

    @Test
    void systemManagedRowIsAPlatformWideOverride() {
        List<FilesRetentionPolicy> rows = List.of(appRow(PREFIX, 30),
                clientRow(PREFIX, "SYSTEM", FilesRetentionPolicyAppliesTo.MANAGED, 90));

        Resolved r = RetentionPolicyResolver.resolve(PREFIX, rows, List.of("C", "M1", "SYSTEM"));

        assertEquals(90, r.days());
        assertEquals("MANAGER:SYSTEM", r.source());
    }

    @Test
    void nullDaysIsKeepForeverWhereverItWins() {
        List<FilesRetentionPolicy> rows = List.of(appRow(PREFIX, 30),
                clientRow(PREFIX, "M1", FilesRetentionPolicyAppliesTo.MANAGED, null));

        Resolved managerOff = RetentionPolicyResolver.resolve(PREFIX, rows, List.of("C", "M1"));
        assertTrue(managerOff.keepForever());

        // A managed client may override its manager again.
        List<FilesRetentionPolicy> withOwn = List.of(appRow(PREFIX, 30),
                clientRow(PREFIX, "M1", FilesRetentionPolicyAppliesTo.MANAGED, null),
                clientRow(PREFIX, "C", FilesRetentionPolicyAppliesTo.SELF, 10));
        Resolved own = RetentionPolicyResolver.resolve(PREFIX, withOwn, List.of("C", "M1"));
        assertFalse(own.keepForever());
        assertEquals(10, own.days());

        Resolved appOff = RetentionPolicyResolver.resolve(PREFIX, List.of(appRow(PREFIX, null)), List.of("C"));
        assertTrue(appOff.keepForever());
    }

    @Test
    void rowsOfOtherPrefixesDoNotApplyAndUnregisteredResolvesToNull() {
        List<FilesRetentionPolicy> rows = List.of(appRow("whatsapp", 30),
                clientRow("whatsapp", "C", FilesRetentionPolicyAppliesTo.SELF, 5),
                appRow(PREFIX, 45));

        Resolved r = RetentionPolicyResolver.resolve(PREFIX, rows, List.of("C"));
        assertEquals(45, r.days());
        assertEquals(RetentionPolicyResolver.SOURCE_APP, r.source());

        assertNull(RetentionPolicyResolver.resolve("nope", rows, List.of("C")));
    }
}
