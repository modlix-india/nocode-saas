package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.billing.MeteringCountDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.test.StepVerifier;

/**
 * Deep DB test for the security-owned metered counts: apps vs sites owned by a
 * client (by APP_TYPE), and the DISTINCT count of a client's users that hold at
 * least one profile in a given app. These raw counts are what the 15-minute rent
 * meter prices, so the type filter, the client scoping and the DISTINCT all matter.
 */
class MeteringCountDAOIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MeteringCountDAO meteringCountDAO;

    private ULong client;
    private ULong appId;   // APP that users hold profiles in
    private ULong app2Id;  // a second APP (different app)

    @BeforeEach
    void setUp() {
        setupMockBeans();
        client = insertTestClient("MCNT", "Metered Client", "BUS").block();
        appId = insertTestApp(client, "mcapp1", "Metered App 1").block();  // APP type
        app2Id = insertTestApp(client, "mcapp2", "Metered App 2").block(); // APP type
        insertSiteApp(client, "mcsite1", "Metered Site 1");                // SITE type
    }

    @AfterEach
    void tearDown() {
        databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
                .then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
                .then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE IN ('mcapp1','mcapp2','mcsite1')").then())
                .then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
                .then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
                .block();
    }

    @Test
    @DisplayName("countAppsOwnedBy counts only APP-type apps of the client")
    void countsAppsByType() {
        StepVerifier.create(meteringCountDAO.countAppsOwnedBy(client))
                .assertNext(n -> assertEquals(2, n)) // mcapp1 + mcapp2, the SITE excluded
                .verifyComplete();
    }

    @Test
    @DisplayName("countSitesOwnedBy counts only SITE-type apps of the client")
    void countsSitesByType() {
        StepVerifier.create(meteringCountDAO.countSitesOwnedBy(client))
                .assertNext(n -> assertEquals(1, n))
                .verifyComplete();
    }

    @Test
    @DisplayName("countUsersWithProfileInApp counts DISTINCT users with >=1 profile in the app")
    void countsDistinctUsersWithProfileInApp() {
        ULong u1 = insertTestUser(client, "mcu1", "mcu1@test.com", "x").block();
        ULong u2 = insertTestUser(client, "mcu2", "mcu2@test.com", "x").block();
        ULong u3 = insertTestUser(client, "mcu3", "mcu3@test.com", "x").block();

        ULong pA = insertProfile(client, appId, "PA");
        ULong pB = insertProfile(client, appId, "PB");
        ULong pOther = insertProfile(client, app2Id, "POther");

        // u1 holds two profiles in the app -> must count once (DISTINCT).
        assignProfile(pA, u1);
        assignProfile(pB, u1);
        // u2 holds one profile in the app.
        assignProfile(pA, u2);
        // u3 holds a profile only in a different app -> excluded by APP_ID filter.
        assignProfile(pOther, u3);

        StepVerifier.create(meteringCountDAO.countUsersWithProfileInApp(client, appId))
                .assertNext(n -> assertEquals(2, n)) // u1 (once) + u2
                .verifyComplete();
    }

    @Test
    @DisplayName("countUsersWithProfileInApp is zero when nobody holds a profile in the app")
    void countsZeroUsersWhenNoProfiles() {
        insertTestUser(client, "lonely", "lonely@test.com", "x").block();
        StepVerifier.create(meteringCountDAO.countUsersWithProfileInApp(client, appId))
                .assertNext(n -> assertEquals(0, n))
                .verifyComplete();
    }

    private void insertSiteApp(ULong clientId, String appCode, String appName) {
        databaseClient.sql(
                "INSERT INTO security_app (CLIENT_ID, APP_NAME, APP_CODE, APP_TYPE) VALUES (:c, :name, :code, 'SITE')")
                .bind("c", clientId.longValue()).bind("name", appName).bind("code", appCode)
                .then().block();
    }

    private ULong insertProfile(ULong clientId, ULong app, String name) {
        return databaseClient.sql(
                "INSERT INTO security_profile (CLIENT_ID, APP_ID, NAME, DESCRIPTION) VALUES (:c, :app, :name, :name)")
                .bind("c", clientId.longValue()).bind("app", app.longValue()).bind("name", name)
                .filter(s -> s.returnGeneratedValues("ID"))
                .map(row -> ULong.valueOf(row.get("ID", Long.class)))
                .one().block();
    }

    private void assignProfile(ULong profileId, ULong userId) {
        databaseClient.sql("INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:p, :u)")
                .bind("p", profileId.longValue()).bind("u", userId.longValue())
                .then().block();
    }
}
