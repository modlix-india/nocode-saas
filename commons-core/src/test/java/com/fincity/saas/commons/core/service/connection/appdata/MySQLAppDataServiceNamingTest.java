package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tenant database naming has to match the Mongo backend exactly.
 *
 * Publish, copyLiveToDraft and the whole draft surface rely on live and draft differing
 * only by the suffix, and on the storage's uniqueName being the table on both. If the
 * two backends ever disagree here, a tenant that moves between them silently loses
 * sight of its own data rather than failing.
 */
class MySQLAppDataServiceNamingTest {

    @Test
    @DisplayName("every operation on the interface is implemented, none left to 501")
    void noMethodLeftBehind() {

        // The backend shipped with seven of fifteen methods answering 501, which was
        // the right way to start and a bad place to stay: a backend that quietly
        // returns an empty page is far worse to debug than one that says it cannot do
        // something, and a method nobody notices is missing is worse still. This
        // fails the moment the interface grows a method this backend has not caught
        // up with.
        for (java.lang.reflect.Method declared : IAppDataService.class.getDeclaredMethods()) {

            if (declared.isDefault() || java.lang.reflect.Modifier.isStatic(declared.getModifiers())) continue;

            boolean implemented = java.util.Arrays.stream(MySQLAppDataService.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().equals(declared.getName())
                            && java.util.Arrays.equals(m.getParameterTypes(), declared.getParameterTypes()));

            assertTrue(implemented, "MySQLAppDataService does not implement " + declared.getName());
        }
    }

    @Test
    @DisplayName("live database is <CLIENT>_<app>")
    void liveName() {
        assertEquals("LZCLA_leadzump", MySQLAppDataService.databaseName("LZCLA", "leadzump", false));
    }

    @Test
    @DisplayName("draft database is the live name plus the shared suffix")
    void draftName() {
        assertEquals(
                "LZCLA_leadzump" + IAppDataService.DRAFT_DB_SUFFIX,
                MySQLAppDataService.databaseName("LZCLA", "leadzump", true));
    }

    @Test
    @DisplayName("a tenant schema name gives its client code back")
    void clientCodeFromDatabase() {
        // The fan-out discovers tenants from the schemas that exist, then has to ask
        // each one's client for its own merged definition. Getting this backwards
        // would resolve the wrong client's schema and migrate the table to a shape
        // that client never asked for.
        assertEquals("LZCLA", MySQLAppDataService.clientCodeOf("LZCLA_leadzump", "leadzump"));
        assertEquals("LZCLA", MySQLAppDataService.clientCodeOf("LZCLA_leadzump_draft", "leadzump"));
        assertEquals("SYSTEM", MySQLAppDataService.clientCodeOf("SYSTEM_kyc", "kyc"));
    }

    @Test
    @DisplayName("round trips with the name it came from")
    void clientCodeRoundTrip() {
        for (boolean draft : new boolean[] {false, true}) {
            String db = MySQLAppDataService.databaseName("LZCLA", "leadzump", draft);
            assertEquals("LZCLA", MySQLAppDataService.clientCodeOf(db, "leadzump"));
            assertEquals(draft, MySQLAppDataService.isDraft(db));
        }
    }

    @Test
    @DisplayName("a client code that contains the app name is not mangled")
    void awkwardClientCode() {
        // Only the trailing "_<app>" is removed, so a client whose own code ends in
        // the app name survives it.
        assertEquals("kyc_team", MySQLAppDataService.clientCodeOf("kyc_team_kyc", "kyc"));
    }

    @Test
    @DisplayName("the suffix comes from the interface, not a local copy")
    void suffixIsTheSharedConstant() {
        // Hard-coding "_draft" here would let the two backends drift apart silently.
        assertTrue(MySQLAppDataService.databaseName("C", "a", true).endsWith(IAppDataService.DRAFT_DB_SUFFIX));
        assertEquals("_draft", IAppDataService.DRAFT_DB_SUFFIX);
    }

    @Test
    @DisplayName("the two surfaces are different databases")
    void surfacesAreDistinct() {
        assertNotEquals(
                MySQLAppDataService.databaseName("C", "app", false), MySQLAppDataService.databaseName("C", "app", true));
    }

    @Test
    @DisplayName("client code is part of the name, so tenants cannot collide")
    void tenantsAreIsolatedByName() {
        assertNotEquals(
                MySQLAppDataService.databaseName("CLIENTA", "app", false),
                MySQLAppDataService.databaseName("CLIENTB", "app", false));
    }

    @Test
    @DisplayName("two apps for one client are different databases")
    void appsAreIsolatedByName() {
        assertNotEquals(
                MySQLAppDataService.databaseName("C", "appone", false),
                MySQLAppDataService.databaseName("C", "apptwo", false));
    }

    @Test
    @DisplayName("a schema name that could escape its backticks is refused rather than rendered")
    void hostileTenantName() {
        // Both halves are platform-issued and conventionally alphanumeric. That is a
        // reason to expect this to pass, not a reason to leave the check out: the
        // name is concatenated into CREATE DATABASE and USE, which cannot be
        // parameterised, so this layer would be relying on an invariant it does not
        // own.
        assertThrows(
                IllegalArgumentException.class,
                () -> MySQLAppDataService.databaseName("a` ; DROP DATABASE x; --", "testapp", false));

        assertEquals("SYSTEM_testapp", MySQLAppDataService.databaseName("SYSTEM", "testapp", false));
        assertEquals("SYSTEM_testapp_draft", MySQLAppDataService.databaseName("SYSTEM", "testapp", true));
    }
}
