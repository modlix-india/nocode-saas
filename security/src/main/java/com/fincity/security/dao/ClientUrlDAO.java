package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fincity.security.jooq.tables.SecurityClient;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientUrlDAO extends AbstractClientCheckDAO<SecurityClientUrlRecord, ULong, ClientUrl> {

    public ClientUrlDAO() {
        super(ClientUrl.class, SECURITY_CLIENT_URL, SECURITY_CLIENT_URL.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_CLIENT_URL.CLIENT_ID;
    }

    @Override
    public Flux<ClientUrl> readAll(AbstractCondition query) {

        return filter(query).flatMapMany(
                condition -> Mono.from(this.dslContext.select(Arrays.asList(table.fields())).from(table).where(condition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<List<String>> getClientUrlsBasedOnAppAndClient(String appCode, ULong clientId) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(SECURITY_CLIENT_URL.APP_CODE.eq(appCode));

        if (clientId != null)
            conditions.add(SECURITY_CLIENT_URL.CLIENT_ID.eq(clientId));

        return Flux.from(
                        this.dslContext.select(SECURITY_CLIENT_URL.URL_PATTERN)
                                .from(SECURITY_CLIENT_URL)
                                .where(DSL.and(conditions)))
                .map(Record1::value1).collectList();
    }

    public Mono<String> getLatestClientUrlBasedOnAppAndClient(String appCode, ULong clientId) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(SECURITY_CLIENT_URL.APP_CODE.eq(appCode));

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

    public Mono<List<ClientUrl>> getClientUrls(String appCode, String clientCode) {

        return Flux.from(this.dslContext.select(SECURITY_CLIENT_URL.fields()).from(SECURITY_CLIENT_URL)
                        .leftJoin(SecurityClient.SECURITY_CLIENT).on(SecurityClient.SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID))
                        .where(SECURITY_CLIENT_URL.APP_CODE.eq(appCode).and(SecurityClient.SECURITY_CLIENT.CODE.eq(clientCode)))
                ).map(rec -> rec.into(ClientUrl.class))
                .collectList();
    }
}
