package com.fincity.security.dao;

import static com.fincity.saas.commons.util.StringUtil.*;
import static com.fincity.security.jooq.tables.SecurityClient.*;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.*;
import static com.fincity.security.jooq.tables.SecurityClientUrl.*;

import java.util.List;

import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.tables.SecurityProfileClientRestriction;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ClientDAO extends AbstractUpdatableDAO<SecurityClientRecord, ULong, Client> {

    protected ClientDAO() {
        super(Client.class, SECURITY_CLIENT, SECURITY_CLIENT.ID);
    }

    public Mono<Tuple2<String, String>> getClientTypeNCode(ULong id) {

        return Flux.from(this.dslContext.select(SECURITY_CLIENT.TYPE_CODE, SECURITY_CLIENT.CODE)
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.ID.eq(id))
                        .limit(1))
                .take(1)
                .singleOrEmpty()
                .map(r -> Tuples.of(r.value1(), r.value2()));
    }

    @Override
    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {

                    Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> x = super.getSelectJointStep();

                    if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                        return x;

                    return x.map(tup -> tup
                            .mapT1(query -> (SelectJoinStep<Record>) query
                                    .leftJoin(SECURITY_CLIENT_HIERARCHY)
                                    .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID
                                            .eq(SECURITY_CLIENT.ID)))
                            .mapT2(query -> query
                                    .leftJoin(SECURITY_CLIENT_HIERARCHY)
                                    .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID
                                            .eq(SECURITY_CLIENT.ID))));
                });
    }

    @Override
    protected Mono<Condition> filter(AbstractCondition condition) {

        return super.filter(condition).flatMap(cond -> SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> {

                    if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                        return cond;

                    ULong clientId = ULong.valueOf(ca.getUser().getClientId());

                    return DSL.and(cond, ClientHierarchyDAO.getManageClientCondition(clientId));
                }));
    }

    public Mono<Client> readInternal(ULong id) {
        return Mono.from(this.dslContext.selectFrom(this.table)
                        .where(this.idField.eq(id))
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Boolean> makeClientActiveIfInActive(ULong clientId) {

        return Mono.from(this.dslContext.update(SECURITY_CLIENT)
                        .set(SECURITY_CLIENT.STATUS_CODE, SecurityClientStatusCode.ACTIVE)
                        .where(SECURITY_CLIENT.ID.eq(clientId)
                                .and(SECURITY_CLIENT.STATUS_CODE
                                        .eq(SecurityClientStatusCode.INACTIVE))))
                .map(e -> e > 0);
    }

    public Mono<Boolean> makeClientInActive(ULong clientId) {

        return Mono.from(this.dslContext.update(SECURITY_CLIENT)
                        .set(SECURITY_CLIENT.STATUS_CODE, SecurityClientStatusCode.INACTIVE)
                        .where(SECURITY_CLIENT.ID.eq(clientId)
                                .and(SECURITY_CLIENT.STATUS_CODE.ne(SecurityClientStatusCode.DELETED))))
                .map(e -> e > 0);
    }

    public Mono<Client> getClientBy(String clientCode) {

        return Flux.from(this.dslContext.select(SECURITY_CLIENT.fields())
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.CODE.eq(clientCode))
                        .limit(1))
                .singleOrEmpty()
                .map(e -> e.into(Client.class));
    }

    public Mono<List<Client>> getClientsBy(List<ULong> clientIds) {

        return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT)
                .where(SECURITY_CLIENT.ID.in(clientIds))).map(e -> e.into(Client.class)).collectList();
    }

    public Flux<ClientUrlPattern> readClientPatterns() {

        return Flux
                .from(this.dslContext
                        .select(SECURITY_CLIENT_URL.CLIENT_ID, SECURITY_CLIENT.CODE,
                                SECURITY_CLIENT_URL.URL_PATTERN,
                                SECURITY_CLIENT_URL.APP_CODE)
                        .from(SECURITY_CLIENT_URL)
                        .leftJoin(SECURITY_CLIENT)
                        .on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID)))
                .map(e -> new ClientUrlPattern(e.value1()
                        .toString(), e.value2(), e.value3(), e.value4()))
                .map(ClientUrlPattern::makeHostnPort);
    }

    public Mono<String> getValidClientCode(String name) {

        name = removeSpecialCharacters(name);

        String clientCode = name.substring(0, Math.min(name.length(), 5)).toUpperCase();

        return Flux.just(clientCode)
                .expand(e -> Mono.from(this.dslContext.select(SECURITY_CLIENT.CODE)
                                .from(SECURITY_CLIENT)
                                .where(SECURITY_CLIENT.CODE.eq(e))
                                .limit(1))
                        .map(Record1::value1)
                        .map(x -> {
                            if (x.length() == clientCode.length())
                                return clientCode + "1";

                            int num = Integer.parseInt(x.substring(clientCode.length()))
                                    + 1;
                            return clientCode + num;
                        }))
                .collectList()
                .map(List::getLast);

    }

    public Mono<ULong> getSystemClientId() {

        return Mono.from(this.dslContext.select(SECURITY_CLIENT.ID)
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.TYPE_CODE.eq("SYS"))
                        .limit(1))
                .map(Record1::value1);
    }

    public Mono<Boolean> isClientActive(List<ULong> clientIds) {
        return Mono.from(this.dslContext.selectCount()
                        .from(SECURITY_CLIENT)
                        .where(SECURITY_CLIENT.STATUS_CODE.eq(SecurityClientStatusCode.ACTIVE))
                        .and(SECURITY_CLIENT.ID.in(clientIds)))
                .map(count -> count.value1() > 0);
    }

    public Mono<Boolean> createProfileRestrictions(ULong clientId, List<ULong> profileIds) {

        return Flux.fromIterable(profileIds).flatMap(profileId -> this.dslContext
                        .insertInto(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .columns(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID,
                                SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                        .values(clientId, profileId))
                .collectList().map(List::size).map(e -> e > 0);
    }
}
