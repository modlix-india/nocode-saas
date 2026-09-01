package com.fincity.security.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.clientcheck.AbstractUpdatableClientCheckDAO;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.enums.SecurityClientUrlUrlType;
import com.fincity.security.jooq.tables.SecurityClient;
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

    public Mono<List<ClientUrl>> getClientUrls(String appCode, String clientCode) {

        return Flux.from(this.dslContext.select(SECURITY_CLIENT_URL.fields()).from(SECURITY_CLIENT_URL)
                .leftJoin(SecurityClient.SECURITY_CLIENT)
                .on(SecurityClient.SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID))
                .where(SECURITY_CLIENT_URL.APP_CODE.eq(appCode)
                        .and(SecurityClient.SECURITY_CLIENT.CODE.eq(clientCode))
                        .and(SECURITY_CLIENT_URL.URL_TYPE.eq(SecurityClientUrlUrlType.LIVE))))
                .map(rec -> rec.into(ClientUrl.class))
                .collectList();
    }
}
