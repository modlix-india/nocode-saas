package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An app-data connection can say "use whatever the platform already uses".
 *
 * Most installs keep app data on the same MySQL the platform talks to, and making
 * every app restate the url, username and password is three chances to get it
 * wrong - and it copies a password into a definition document that then gets
 * TRANSPORTED between environments, so staging credentials follow the app into
 * production.
 */
class MySQLDefaultConnectionTest {

    private static final String DEF_URL = "r2dbc:mysql://platform-host:3306/core";
    private static final String DEF_USER = "platform";
    private static final String DEF_PASS = "platform-pw";

    private static Map<String, Object> details(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static MySQLAppDataService.Credentials resolve(Map<String, Object> details) {
        return MySQLAppDataService.credentials(details, DEF_URL, DEF_USER, DEF_PASS);
    }

    @Test
    @DisplayName("useDefaultConnection takes the platform's own datasource")
    void takesThePlatformDatasource() {

        MySQLAppDataService.Credentials c = resolve(details("useDefaultConnection", true));

        assertEquals(DEF_URL, c.url());
        assertEquals(DEF_USER, c.username());
        assertEquals(DEF_PASS, c.password());
        assertTrue(c.fromDefault());
    }

    @Test
    @DisplayName("An explicit connection is untouched")
    void explicitDetailsWin() {

        MySQLAppDataService.Credentials c =
                resolve(details("url", "r2dbc:mysql://other:3306/", "username", "u", "password", "p"));

        assertEquals("r2dbc:mysql://other:3306/", c.url());
        assertEquals("u", c.username());
        assertFalse(c.fromDefault());
    }

    /**
     * All or nothing. Taking the url from the default and the password from the
     * document would produce a combination nobody wrote down, and the failure would
     * read as a wrong password rather than a mixed-up source.
     */
    @Test
    @DisplayName("Choosing the default ignores any leftover url and password on the document")
    void defaultIsAllOrNothing() {

        MySQLAppDataService.Credentials c = resolve(details(
                "useDefaultConnection", true,
                "url", "r2dbc:mysql://stale:3306/",
                "username", "stale",
                "password", "stale-pw"));

        assertEquals(DEF_URL, c.url());
        assertEquals(DEF_USER, c.username());
        assertEquals(DEF_PASS, c.password());
    }

    @Test
    @DisplayName("The flag off behaves exactly as before the flag existed")
    void flagOffIsUnchanged() {

        MySQLAppDataService.Credentials c =
                resolve(details("useDefaultConnection", false, "url", "r2dbc:mysql://mine:3306/"));

        assertEquals("r2dbc:mysql://mine:3306/", c.url());
        assertFalse(c.fromDefault());
    }

    @Test
    @DisplayName("Blank details yield no url, so the caller still reports what is missing")
    void blankStaysBlank() {

        assertNull(resolve(details()).url());
        assertNull(resolve(null).url());
        assertNull(resolve(details("url", "   ")).url());
        assertFalse(resolve(null).fromDefault());
    }

    /**
     * The flag is read from a definition document, so it arrives as whatever JSON
     * carried - a real boolean from the form, a string from a hand-written one.
     */
    @Test
    @DisplayName("The flag is honoured whether it arrives as a boolean or a string")
    void acceptsEitherJsonShape() {

        assertTrue(resolve(details("useDefaultConnection", true)).fromDefault());
        assertTrue(resolve(details("useDefaultConnection", "true")).fromDefault());
        assertFalse(resolve(details("useDefaultConnection", "false")).fromDefault());
        assertFalse(resolve(details("useDefaultConnection", null)).fromDefault());
    }
}
