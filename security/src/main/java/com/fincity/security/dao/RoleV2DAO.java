package com.fincity.security.dao;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.jooq.tables.*;
import com.fincity.security.util.AuthoritiesNameUtil;
import io.r2dbc.spi.Result;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityV2Role.SECURITY_V2_ROLE;
import static com.fincity.security.jooq.tables.SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE;

import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RoleV2DAO extends AbstractClientCheckDAO<SecurityV2RoleRecord, ULong, RoleV2> {

    public RoleV2DAO() {
        super(RoleV2.class, SecurityV2Role.SECURITY_V2_ROLE, SecurityV2Role.SECURITY_V2_ROLE.CLIENT_ID);
    }

    @Override
    public Field<ULong> getClientIDField() {
        return SecurityV2Role.SECURITY_V2_ROLE.CLIENT_ID;
    }


    // This method will get all the roles in the roleIds and their sub roles too
    public Mono<List<RoleV2>> getRoles(Collection<ULong> roleIds) {

        Mono<Map<ULong, List<RoleV2>>> subRoles = Flux.from(this.dslContext.select(SECURITY_V2_ROLE_ROLE.ROLE_ID).select(SECURITY_V2_ROLE.fields())
                        .from(SECURITY_V2_ROLE_ROLE)
                        .leftJoin(SECURITY_V2_ROLE).on(SECURITY_V2_ROLE.ID.eq(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID))
                        .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.in(roleIds)))
                .collect(Collectors.groupingBy(rec -> rec.get(SECURITY_V2_ROLE_ROLE.ROLE_ID), Collectors.mapping(rec -> rec.into(RoleV2.class), Collectors.toList())));

        return subRoles.flatMap(map ->
                Flux.from(this.dslContext.selectFrom(SECURITY_V2_ROLE).where(SECURITY_V2_ROLE.ID.in(roleIds)))
                        .map(rec -> rec.into(RoleV2.class))
                        .map(role -> role.setSubRoles(map.get(role.getId())))
                        .collectList());
    }

    public Mono<Map<String, List<String>>> getRoleAuthoritiesPerApp(ULong userId) {

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext
                                .select(SECURITY_APP.APP_CODE, SECURITY_V2_ROLE.NAME, SECURITY_V2_ROLE.ID)
                                .from(SECURITY_V2_ROLE)
                                .leftJoin(SECURITY_APP)
                                .on(SECURITY_V2_ROLE.APP_ID.eq(SECURITY_APP.ID))
                                .where(SECURITY_V2_ROLE.ID.in(
                                        this.dslContext
                                                .select(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID)
                                                .from(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE)
                                                .leftJoin(SecurityV2UserRole.SECURITY_V2_USER_ROLE)
                                                .on(SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID
                                                        .eq(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.ROLE_ID))
                                                .where(SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID.eq(userId))
                                                .union(
                                                        this.dslContext
                                                                .select(SecurityV2UserRole.SECURITY_V2_USER_ROLE.ROLE_ID)
                                                                .from(SecurityV2UserRole.SECURITY_V2_USER_ROLE)
                                                                .where(SecurityV2UserRole.SECURITY_V2_USER_ROLE.USER_ID
                                                                        .eq(userId))))))
                        .distinct()
                        .collectList(),

                roles -> Flux
                        .from(this.dslContext.select(SECURITY_APP.APP_CODE, SecurityPermission.SECURITY_PERMISSION.NAME)
                                .from(SecurityPermission.SECURITY_PERMISSION)
                                .leftJoin(SECURITY_APP)
                                .on(SecurityPermission.SECURITY_PERMISSION.APP_ID.eq(SECURITY_APP.ID))
                                .leftJoin(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION)
                                .on(SecurityPermission.SECURITY_PERMISSION.ID
                                        .eq(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.PERMISSION_ID))
                                .where(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.ROLE_ID
                                        .in(roles.stream().map(Record3::value3).collect(Collectors.toList()))))
                        .distinct()
                        .collectList(),

                (roles, permissions) -> Mono.just(

                        Stream.concat(
                                        roles.stream()
                                                .map(e -> Tuples.of(CommonsUtil.nonNullValue(e.getValue(SecurityApp.SECURITY_APP.APP_CODE), ""), AuthoritiesNameUtil.makeRoleName(
                                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                                        e.getValue(SecurityV2Role.SECURITY_V2_ROLE.NAME)))),
                                        permissions
                                                .stream()
                                                .map(e -> Tuples.of(CommonsUtil.nonNullValue(e.getValue(SecurityApp.SECURITY_APP.APP_CODE), ""), AuthoritiesNameUtil.makePermissionName(
                                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                                        e.getValue(SecurityPermission.SECURITY_PERMISSION.NAME)))))
                                .collect(Collectors.groupingBy(Tuple2::getT1, Collectors.mapping(Tuple2::getT2, Collectors.toList())))
                )

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserDAO.getRoleAuthorities"));
    }


    public Mono<List<RoleV2>> getRolesForAssignmentInApp(String appCode, ClientHierarchy hierarchy) {

        return Flux.from(this.dslContext.selectFrom(SECURITY_V2_ROLE).where(DSL.and(
                SECURITY_V2_ROLE.CLIENT_ID.eq(hierarchy.getClientId()),
                "nothing".equals(appCode) ? DSL.trueCondition() :
                        DSL.or(SECURITY_V2_ROLE.APP_ID.eq(this.dslContext.select(SECURITY_APP.ID).from(SECURITY_APP)
                                        .where(SECURITY_APP.APP_CODE.eq(appCode))),
                                SECURITY_V2_ROLE.APP_ID.isNull()
                        )
        ))).map(r -> r.into(RoleV2.class)).collectList();
    }

    public Mono<List<RoleV2>> fetchSubRoles(List<ULong> roleIds) {

        return Flux.from(this.dslContext.select(SECURITY_V2_ROLE.fields()).from(SECURITY_V2_ROLE_ROLE)
                .leftJoin(SECURITY_V2_ROLE).on(SECURITY_V2_ROLE.ID.eq(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID))
                .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.in(roleIds))).map(r -> r.into(RoleV2.class)).collectList();
    }
}