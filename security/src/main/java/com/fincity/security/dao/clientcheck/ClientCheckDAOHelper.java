package com.fincity.security.dao.clientcheck;

import static com.fincity.security.jooq.tables.SecurityClient.*;
import static com.fincity.security.jooq.tables.SecurityClientHierarchy.*;
import static com.fincity.security.jooq.tables.SecurityClientManager.*;

import java.util.Arrays;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.ClientHierarchyDAO;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@UtilityClass
public class ClientCheckDAOHelper {

    private static <R extends UpdatableRecord<R>> Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> createClientBaseQueries(
            DSLContext dslContext, Table<R> table) {

        SelectJoinStep<Record> mainQuery = dslContext.select(Arrays.asList(table.fields())).from(table);

        SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.count()).from(table);

        return Tuples.of(mainQuery, countQuery);
    }

    public static Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> addJoinCondition(
            SelectJoinStep<Record> mainQuery, SelectJoinStep<Record1<Integer>> countQuery, Field<ULong> clientIdField) {

        return Tuples.of(
                mainQuery
                        .leftJoin(SECURITY_CLIENT)
                        .on(SECURITY_CLIENT.ID.eq(clientIdField))
                        .leftJoin(SECURITY_CLIENT_HIERARCHY)
                        .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_CLIENT.ID))
                        .leftJoin(SECURITY_CLIENT_MANAGER)
                        .on(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientIdField)),
                countQuery
                        .leftJoin(SECURITY_CLIENT)
                        .on(SECURITY_CLIENT.ID.eq(clientIdField))
                        .leftJoin(SECURITY_CLIENT_HIERARCHY)
                        .on(SECURITY_CLIENT_HIERARCHY.CLIENT_ID.eq(SECURITY_CLIENT.ID))
                        .leftJoin(SECURITY_CLIENT_MANAGER)
                        .on(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientIdField)));
    }

    public static <R extends UpdatableRecord<R>> Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep(
            DSLContext dslContext, Table<R> table, Field<ULong> clientIdField) {

        return SecurityContextUtil.getUsersContextAuthentication().map(ca -> {
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> baseQueries = createClientBaseQueries(
                    dslContext, table);

            return ca.getClientTypeCode().equals(ContextAuthentication.CLIENT_TYPE_SYSTEM)
                    ? baseQueries
                    : addJoinCondition(baseQueries.getT1(), baseQueries.getT2(), clientIdField);
        });
    }

    private static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    public static Mono<Condition> applyClientFilter(Mono<Condition> condition) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {

                    if (ca.getClientTypeCode().equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
                        return condition;

                    ULong userClientId = ULong.valueOf(ca.getUser().getClientId());
                    ULong userId = ULong.valueOf(ca.getUser().getId());
                    boolean isOwner = SecurityContextUtil.hasAuthority(OWNER_ROLE, ca.getAuthorities());

                    Condition clientCondition = DSL.and(
                            ClientHierarchyDAO.getManageClientCondition(userClientId),
                            isOwner ? DSL.trueCondition() : SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(userId));

                    return condition.map(c -> DSL.and(c, clientCondition));
                })
                .switchIfEmpty(condition);
    }
}
