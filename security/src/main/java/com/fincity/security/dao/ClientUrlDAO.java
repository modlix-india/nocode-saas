package com.fincity.security.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.clientcheck.AbstractUpdatableClientCheckDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.enums.SecurityClientUrlUrlType;
import com.fincity.security.jooq.tables.SecurityClient;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;
import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientUrlDAO extends AbstractUpdatableClientCheckDAO<SecurityClientUrlRecord, ULong, ClientUrl> {

    public ClientUrlDAO() {
        super(ClientUrl.class, SECURITY_CLIENT_URL, SECURITY_CLIENT_URL.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_CLIENT_URL.CLIENT_ID;
    }

    @Override
    public Flux<ClientUrl> readAll(AbstractCondition query) {

        return filter(query, null).flatMapMany(
                condition -> Mono
                        .from(this.dslContext.select(Arrays.asList(table.fields())).from(table).where(condition))
                        .map(e -> e.into(this.pojoClass)));
    }

    /**
     * LIVE rows only, and the same for every general-purpose reader below.
     *
     * A DRAFT row is a bearer credential: its whole value is that the hostname is
     * unguessable. These queries feed things that hand a URL back to a caller or
     * treat it as the app's address, so an unfiltered read leaks the draft host to
     * anyone who can ask an app for its URLs, and worse, lets a draft host be
     * picked as the app's canonical one. getDraftUrl above is the only reader that
     * should see DRAFT, and it asks for it explicitly.
     */
    public Mono<List<String>> getClientUrlsBasedOnAppAndClient(String appCode, ULong clientId) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(SECURITY_CLIENT_URL.APP_CODE.eq(appCode));
        conditions.add(SECURITY_CLIENT_URL.URL_TYPE.eq(SecurityClientUrlUrlType.LIVE));

        if (clientId != null)
            conditions.add(SECURITY_CLIENT_URL.CLIENT_ID.eq(clientId));

        return Flux.from(
                this.dslContext.select(SECURITY_CLIENT_URL.URL_PATTERN)
                        .from(SECURITY_CLIENT_URL)
                        .where(DSL.and(conditions)))
                .map(Record1::value1).collectList();
    }

    /** LIVE only. A freshly minted draft is the most recently updated row. */
    public Mono<String> getLatestClientUrlBasedOnAppAndClient(String appCode, ULong clientId) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(SECURITY_CLIENT_URL.APP_CODE.eq(appCode));
        conditions.add(SECURITY_CLIENT_URL.URL_TYPE.eq(SecurityClientUrlUrlType.LIVE));

        if (clientId != null)
            conditions.add(SECURITY_CLIENT_URL.CLIENT_ID.eq(clientId));

        return Mono.from(
                this.dslContext.select(SECURITY_CLIENT_URL.URL_PATTERN)
                        .from(SECURITY_CLIENT_URL)
                        .where(DSL.and(conditions))
                        .orderBy(SECURITY_CLIENT_URL.UPDATED_AT.desc())
                        .limit(1))
                .map(stringRecord1 -> stringRecord1.into(String.class));
    }

    public Mono<Boolean> checkSubDomainAvailability(String subDomain) {

        return Mono.from(this.dslContext.selectCount()
                .from(SECURITY_CLIENT_URL)
                .where(SECURITY_CLIENT_URL.URL_PATTERN.eq(subDomain))
                .limit(1))
                .map(e -> e.value1() == 0);
    }

    /**
     * The first of these patterns that somebody already holds, or empty.
     *
     * One query rather than one per candidate: app creation checks the hostname
     * the new app would answer on under every configured subdomain ending, and
     * that is a list, not a value.
     *
     * Compared case-insensitively and against the bare hostname only, because the
     * column holds whatever was typed: 527 of the 570 rows on dev are bare hosts
     * such as `kk28.dev.modlix.com`, but the column permits a scheme and the
     * resolver strips one, so an `https://` row on the same hostname resolves the
     * same way and has to count as taken.
     */
    public Mono<String> firstTakenPattern(List<String> patterns) {

        if (patterns == null || patterns.isEmpty())
            return Mono.empty();

        List<Condition> conditions = new ArrayList<>();

        for (String pattern : patterns) {
            conditions.add(SECURITY_CLIENT_URL.URL_PATTERN.equalIgnoreCase(pattern));
            conditions.add(SECURITY_CLIENT_URL.URL_PATTERN.equalIgnoreCase("http://" + pattern));
            conditions.add(SECURITY_CLIENT_URL.URL_PATTERN.equalIgnoreCase("https://" + pattern));
        }

        return Mono.from(this.dslContext.select(SECURITY_CLIENT_URL.URL_PATTERN)
                .from(SECURITY_CLIENT_URL)
                .where(DSL.or(conditions))
                .limit(1))
                .map(Record1::value1);
    }

    /**
     * The single DRAFT row for an app and client, if one has been minted.
     *
     * "At most one per (client, app)" cannot be a unique constraint here: MySQL has
     * no partial unique index, and the LIVE rows must stay unconstrained. So the
     * invariant is enforced in ClientUrlService, which reads through this.
     */
    public Mono<ClientUrl> getDraftUrl(String appCode, ULong clientId) {

        return Mono.from(this.dslContext.select(SECURITY_CLIENT_URL.fields()).from(SECURITY_CLIENT_URL)
                .where(SECURITY_CLIENT_URL.APP_CODE.eq(appCode)
                        .and(SECURITY_CLIENT_URL.CLIENT_ID.eq(clientId))
                        .and(SECURITY_CLIENT_URL.URL_TYPE.eq(SecurityClientUrlUrlType.DRAFT)))
                .limit(1))
                .map(rec -> rec.into(ClientUrl.class));
    }

    /**
     * The character that escapes a literal {@code %} or {@code _} in the two
     * substring filters below. Any character not otherwise special will do; a
     * backslash would need doubling through both JOOQ and MySQL, so this is one
     * fewer thing to get wrong.
     */
    private static final char LIKE_ESCAPE = '!';

    /**
     * An app's LIVE URLs, a page at a time, with each row's client named rather
     * than only numbered.
     *
     * `clientCode` is OPTIONAL. Blank means every client's rows, which is what
     * the workspace's URLs & SSL pane lists: an app's addresses are spread
     * across clients (527 of them on cxapp) and showing one client's at a time
     * hid the rest. The picker there narrows this rather than defining it.
     *
     * `restrictToClientId` is what keeps that honest. This is a hand-written
     * query, so the generic `applyClientFilter` that scopes the paged listing
     * route does NOT apply here; without this, dropping the client condition
     * would hand every client's hostnames to anyone with read access to the app.
     * The service passes null only for a SYSTEM caller.
     *
     * `urlPattern` and `clientName` are substring filters, applied server-side
     * so that a client with hundreds of addresses is searchable without shipping
     * all of them. Both are case-insensitive by collation, not by `lower()`, so
     * the index on URL_PATTERN is still usable for the non-leading-wildcard part
     * of a match. Unlike the generic `/query` route -- which interpolates
     * STRING_LOOSE_EQUAL straight into `like("%" + value + "%")` -- a `%` or `_`
     * typed here matches itself: a hostname is exactly the kind of value that
     * contains an underscore, and a filter that silently turns it into "any
     * character" gives wrong rows with no sign that it did.
     *
     * The sort is FIXED at client name, then pattern, then id, and
     * `pageable.getSort()` is ignored. The id is not decoration: paging over a
     * non-total order lets MySQL repeat a row on one page and drop it from
     * another, and two rows can share a client and a pattern only differing by
     * URL type, which the caller cannot see.
     *
     * CODE and NAME are selected under the DTO's own field names so `into`
     * fills them; see the note on those fields for why the write path does not
     * mind.
     */
    public Mono<Page<ClientUrl>> getClientUrls(String appCode, String clientCode, ULong restrictToClientId,
            String urlPattern, String clientName, Pageable pageable) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(SECURITY_CLIENT_URL.APP_CODE.eq(appCode));
        conditions.add(SECURITY_CLIENT_URL.URL_TYPE.eq(SecurityClientUrlUrlType.LIVE));

        if (!StringUtil.safeIsBlank(clientCode))
            conditions.add(SecurityClient.SECURITY_CLIENT.CODE.eq(clientCode));

        if (!StringUtil.safeIsBlank(urlPattern))
            conditions.add(contains(SECURITY_CLIENT_URL.URL_PATTERN, urlPattern));

        if (!StringUtil.safeIsBlank(clientName))
            conditions.add(contains(SecurityClient.SECURITY_CLIENT.NAME, clientName));

        if (restrictToClientId != null)
            conditions.add(SECURITY_CLIENT_URL.CLIENT_ID.in(
                    this.dslContext.select(SECURITY_CLIENT_HIERARCHY.CLIENT_ID)
                            .from(SECURITY_CLIENT_HIERARCHY)
                            .where(ClientHierarchyDAO.getManageClientCondition(restrictToClientId))));

        Condition where = DSL.and(conditions);

        List<Field<?>> fields = new ArrayList<>(Arrays.asList(SECURITY_CLIENT_URL.fields()));
        fields.add(SecurityClient.SECURITY_CLIENT.CODE.as("client_code"));
        fields.add(SecurityClient.SECURITY_CLIENT.NAME.as("client_name"));

        // The count carries the SAME left join, not because it needs a column
        // from it but because `clientCode` and `clientName` filter on it. A
        // count over the bare table would answer for a different query.
        Mono<Integer> count = Mono.from(this.dslContext.selectCount().from(SECURITY_CLIENT_URL)
                .leftJoin(SecurityClient.SECURITY_CLIENT)
                .on(SecurityClient.SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID))
                .where(where))
                .map(Record1::value1);

        Mono<List<ClientUrl>> rows = Flux.from(this.dslContext.select(fields).from(SECURITY_CLIENT_URL)
                .leftJoin(SecurityClient.SECURITY_CLIENT)
                .on(SecurityClient.SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID))
                .where(where)
                .orderBy(SecurityClient.SECURITY_CLIENT.NAME.asc(), SECURITY_CLIENT_URL.URL_PATTERN.asc(),
                        SECURITY_CLIENT_URL.ID.asc())
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset()))
                .map(rec -> rec.into(ClientUrl.class))
                .collectList();

        return rows.flatMap(list -> count.map(c -> PageableExecutionUtils.getPage(list, pageable, () -> c)));
    }

    /** A case-insensitive substring match in which `%` and `_` are literal. */
    private static Condition contains(Field<String> field, String value) {
        return field.like("%" + DSL.escape(value, LIKE_ESCAPE) + "%", LIKE_ESCAPE);
    }
}
