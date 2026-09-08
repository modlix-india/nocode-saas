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

    private static <R extends UpdatableRecord<R>> Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> createDistinctClientBaseQueries(
            DSLContext dslContext, Table<R> table) {

        SelectJoinStep<Record> mainQuery = dslContext.selectDistinct(Arrays.asList(table.fields())).from(table);

        SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.countDistinct(table.field("ID")))
                .from(table);

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

            if (ca.getClientTypeCode().equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
                return createClientBaseQueries(dslContext, table);

            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> baseQueries = createDistinctClientBaseQueries(
                    dslContext, table);

            return addJoinCondition(baseQueries.getT1(), baseQueries.getT2(), clientIdField);
        });
    }

    private static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    private static final String MANAGING_ROLES = "Authorities.ROLE_Owner or Authorities.ROLE_ClientManager";

    /**
     * SQL mirror of {@code ClientService.isUserClientManageClient(ca, clientId)}:
     * a row is visible when it belongs to the caller's own client, or to a client
     * below it in the hierarchy that this caller actually manages - either by
     * holding a managing role or by being registered as a manager of that client.
     * <p>
     * This differs from {@link #applyClientFilter(Mono)} in one way that matters:
     * the caller's own client is always visible. {@code applyClientFilter} also
     * demands a {@code SECURITY_CLIENT_MANAGER} row for a non-owner, which no one
     * has for their own client, so it hides an administrator's own organisation
     * from them. Use this variant for anything an administrator raises and acts on
     * inside their own client.
     * <p>
     * The client id is taken from the signed-in user's context authentication -
     * never from a caller supplied header. With no authentication at all nothing
     * is visible.
     * <p>
     * The joins this needs are added by
     * {@link #getSelectJointStep(DSLContext, Table, Field)}, which leaves them out
     * for a SYSTEM client - so this returns the condition untouched for SYSTEM.
     *
     * @param clientIdField the client id column of the table being filtered
     */
    public static Mono<Condition> applyOwnAndManagedClientFilter(
            Mono<Condition> condition, Field<ULong> clientIdField) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {

                    if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                        return condition;

                    ULong userClientId = ULong.valueOf(ca.getUser().getClientId());
                    ULong userId = ULong.valueOf(ca.getUser().getId());

                    Condition managedClient = DSL.and(
                            ClientHierarchyDAO.getManageClientCondition(userClientId),
                            SecurityContextUtil.hasAuthority(MANAGING_ROLES, ca.getAuthorities())
                                    ? DSL.trueCondition()
                                    : SECURITY_CLIENT_MANAGER.MANAGER_ID.eq(userId));

                    return condition.map(c -> DSL.and(c,
                            DSL.or(clientIdField.eq(userClientId), managedClient)));
                })
                .switchIfEmpty(Mono.just(DSL.falseCondition()));
    }

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
