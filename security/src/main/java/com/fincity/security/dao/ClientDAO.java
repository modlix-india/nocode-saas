package com.fincity.security.dao;

import java.util.List;
import java.util.Objects;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
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
import static com.fincity.saas.commons.util.StringUtil.removeSpecialCharacters;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Profile;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;
import static com.fincity.security.jooq.tables.SecurityClientProfile.SECURITY_CLIENT_PROFILE;
import static com.fincity.security.jooq.tables.SecurityProfile.SECURITY_PROFILE;
import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;
import static com.fincity.security.jooq.tables.SecurityV2Role.SECURITY_V2_ROLE;
import static com.fincity.security.jooq.tables.SecurityProfileRole.SECURITY_PROFILE_ROLE;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.jooq.tables.records.SecurityClientProfileRecord;

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

    public Mono<Boolean> assignProfileToClient(ULong clientId, ULong profileId) {

        return Mono
                .from(this.dslContext
                        .insertInto(SECURITY_CLIENT_PROFILE, SECURITY_CLIENT_PROFILE.CLIENT_ID,
                                SECURITY_CLIENT_PROFILE.PROFILE_ID)
                        .values(clientId, profileId))
                .map(val -> val > 0);

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

    public Mono<Boolean> checkRoleExistsOrCreatedForClient(ULong clientId, ULong roleId) {

        Condition profileCondition = SECURITY_CLIENT_PROFILE.CLIENT_ID.eq(clientId);
        Condition roleCondition = SECURITY_V2_ROLE.ID.eq(roleId);
        Condition roleExistsCondition = profileCondition.and(roleCondition);
        Condition roleCreatedCondition = SECURITY_V2_ROLE.CLIENT_ID.eq(clientId);

        return Mono.from(

                this.dslContext.selectCount()
                        .from(SECURITY_V2_ROLE)
                        .leftJoin(SECURITY_PROFILE_ROLE)
                        .on(SECURITY_V2_ROLE.ID.eq(SECURITY_PROFILE_ROLE.ROLE_ID))
                        .leftJoin(SECURITY_PROFILE)
                        .on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_ROLE.PROFILE_ID))
                        .leftJoin(SECURITY_CLIENT_PROFILE)
                        .on(SECURITY_CLIENT_PROFILE.PROFILE_ID
                                .eq(SECURITY_PROFILE.ID))
                        .where(roleExistsCondition.or(roleCreatedCondition)))

                .map(Record1::value1)
                .map(val -> val > 0);
    }

    public Mono<Boolean> removeProfile(ULong clientId, ULong profileId) {

        DeleteQuery<SecurityClientProfileRecord> query = this.dslContext.deleteQuery(SECURITY_CLIENT_PROFILE);

        query.addConditions(SECURITY_CLIENT_PROFILE.PROFILE_ID.eq(profileId)
                .and(SECURITY_CLIENT_PROFILE.CLIENT_ID.eq(clientId)));

        return Mono.from(query)
                .map(val -> val == 1);
    }

    public Mono<Profile> getProfile(ULong profileId) {

        return Mono.from(this.dslContext.select(SECURITY_PROFILE.fields())
                .from(SECURITY_PROFILE)
                .where(SECURITY_PROFILE.ID.eq(profileId))
                .limit(1))
                .filter(Objects::nonNull)
                .map(e -> e.into(Profile.class));
    }

    public Mono<Boolean> checkProfileAssignedForClient(ULong clientId, ULong profileId) {

        return Mono.from(

                this.dslContext.selectCount()
                        .from(SECURITY_CLIENT_PROFILE)
                        .where(SECURITY_CLIENT_PROFILE.CLIENT_ID.eq(clientId)
                                .and(SECURITY_CLIENT_PROFILE.PROFILE_ID.eq(profileId))))
                .map(Record1::value1)
                .map(value -> value == 1);
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

    public Mono<List<Profile>> getProfilesAvailableForClient(ULong clientId) {

        return Flux.from(

                this.dslContext.select(SECURITY_PROFILE.fields())
                        .from(SECURITY_PROFILE)
                        .leftJoin(SECURITY_CLIENT_PROFILE)
                        .on(SECURITY_PROFILE.ID.eq(SECURITY_CLIENT_PROFILE.PROFILE_ID))

                        .where(SECURITY_CLIENT_PROFILE.CLIENT_ID.eq(clientId)))
                .map(e -> e.into(Profile.class))
                .collectList();
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
}
