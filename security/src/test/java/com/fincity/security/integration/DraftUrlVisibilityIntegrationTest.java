package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.enums.ClientUrlType;

/**
 * A DRAFT client URL is a bearer credential and must not surface through any
 * general-purpose URL reader.
 *
 * The whole security model of the draft surface is that its hostname is
 * unguessable: it serves anonymous traffic, so anyone holding the URL sees the
 * app's unpublished work. Three DAO readers queried `security_client_url` with no
 * URL_TYPE filter, so a draft row was returned to everything that asks an app for
 * its URLs. Worse than the leak, `getLatestClientUrlBasedOnAppAndClient` orders by
 * UPDATED_AT and takes one: a freshly minted draft is by definition the most
 * recently updated row, so it would be picked as the app's canonical URL.
 *
 * getDraftUrl is the single reader that should see DRAFT, and it asks for it by
 * name.
 */
@DisplayName("Draft URLs are invisible to the general readers")
class DraftUrlVisibilityIntegrationTest extends AbstractIntegrationTest {

    private static final String LIVE_HOST = "draftvis-live.dev.modlix.com";
    private static final String DRAFT_HOST = "d0123456789abcdef0123456789abcdef.dev.modlix.com";

    @Autowired
    private ClientUrlDAO clientUrlDAO;

    private ULong clientId;
    private String clientCode;
    private String appCode;

    /**
     * These suites share one reused container database, so this seeds against an
     * app and client that already exist and removes only the two rows it added,
     * by URL_PATTERN. Inventing an app code is not an option: security_client_url
     * has a foreign key onto security_app.
     */
    @BeforeEach
    void seed() {

        setupMockBeans();

        Object[] client = this.databaseClient.sql("SELECT ID, CODE FROM security_client WHERE CODE = 'SYSTEM'")
                .map((r, m) -> new Object[] { ULong.valueOf(r.get("ID", java.math.BigInteger.class)),
                        r.get("CODE", String.class) })
                .one().block();
        this.clientId = (ULong) client[0];
        this.clientCode = (String) client[1];

        this.appCode = this.databaseClient
                .sql("SELECT APP_CODE FROM security_app WHERE CLIENT_ID = :cid ORDER BY ID LIMIT 1")
                .bind("cid", this.clientId.toBigInteger())
                .map((r, m) -> r.get("APP_CODE", String.class))
                .one().block();

        cleanup();

        // The live row first, then the draft, so the draft is the most recently
        // updated. That ordering is what getLatestClientUrlBasedOnAppAndClient keys
        // on and is the case that actually hijacks the app's URL.
        insert(LIVE_HOST, "LIVE");
        insert(DRAFT_HOST, "DRAFT");
    }

    @AfterEach
    void cleanup() {
        this.databaseClient.sql("DELETE FROM security_client_url WHERE URL_PATTERN IN (:live, :draft)")
                .bind("live", LIVE_HOST).bind("draft", DRAFT_HOST)
                .fetch().rowsUpdated().block();
    }

    private void insert(String pattern, String type) {
        this.databaseClient.sql(
                "INSERT INTO security_client_url (CLIENT_ID, APP_CODE, URL_PATTERN, URL_TYPE) "
                        + "VALUES (:cid, :app, :pattern, :type)")
                .bind("cid", this.clientId.toBigInteger())
                .bind("app", this.appCode)
                .bind("pattern", pattern)
                .bind("type", type)
                .fetch().rowsUpdated().block();
    }

    @Test
    @DisplayName("the latest URL for an app is never the draft, however recently it was minted")
    void latestUrlSkipsDraft() {

        String latest = this.clientUrlDAO.getLatestClientUrlBasedOnAppAndClient(this.appCode, this.clientId).block();

        assertThat(latest)
                .as("a freshly minted draft host was handed back as the app's canonical URL")
                .isEqualTo(LIVE_HOST);
    }

    @Test
    @DisplayName("listing an app's URLs does not include the draft")
    void urlListSkipsDraft() {

        List<String> urls = this.clientUrlDAO.getClientUrlsBasedOnAppAndClient(this.appCode, this.clientId).block();

        assertThat(urls).contains(LIVE_HOST);
        assertThat(urls)
                .as("the draft hostname leaked to anyone who can list an app's URLs")
                .doesNotContain(DRAFT_HOST);
    }

    @Test
    @DisplayName("the client URL listing does not include the draft either")
    void clientUrlsSkipDraft() {

        List<ClientUrl> urls = this.clientUrlDAO.getClientUrls(this.appCode, this.clientCode, null).block();

        assertThat(urls).extracting(ClientUrl::getUrlPattern).contains(LIVE_HOST);
        assertThat(urls).extracting(ClientUrl::getUrlPattern)
                .as("the draft hostname leaked through the client URL listing")
                .doesNotContain(DRAFT_HOST);
    }

    @Test
    @DisplayName("getDraftUrl still finds it, since that is the one reader meant to")
    void draftReaderStillWorks() {

        ClientUrl draft = this.clientUrlDAO.getDraftUrl(this.appCode, this.clientId).block();

        assertThat(draft).as("filtering the general readers must not break the explicit one").isNotNull();
        assertThat(draft.getUrlPattern()).isEqualTo(DRAFT_HOST);
        assertThat(draft.getUrlType()).isEqualTo(ClientUrlType.DRAFT);
    }
}
