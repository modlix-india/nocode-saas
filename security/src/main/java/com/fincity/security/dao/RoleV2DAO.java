package com.fincity.security.dao;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.clientcheck.AbstractUpdatableClientCheckDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.jooq.tables.*;
import com.fincity.security.util.AuthoritiesNameUtil;

import org.jooq.Field;
import org.jooq.Record1;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RoleV2DAO extends AbstractUpdatableClientCheckDAO<SecurityV2RoleRecord, ULong, RoleV2> {

    public RoleV2DAO() {
        super(RoleV2.class, SecurityV2Role.SECURITY_V2_ROLE, SecurityV2Role.SECURITY_V2_ROLE.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SecurityV2Role.SECURITY_V2_ROLE.CLIENT_ID;
    }

    // This method will get all the roles in the roleIds and their sub roles too
    public Mono<List<RoleV2>> getRoles(Collection<ULong> roleIds) {

        Mono<Map<ULong, List<RoleV2>>> subRoles = Flux
                .from(this.dslContext.select(SECURITY_V2_ROLE_ROLE.ROLE_ID)
                        .select(SECURITY_V2_ROLE.fields())
                        .from(SECURITY_V2_ROLE_ROLE)
                        .leftJoin(SECURITY_V2_ROLE)
                        .on(SECURITY_V2_ROLE.ID.eq(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID))
                        .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.in(roleIds)))
                .collect(Collectors.groupingBy(rec -> rec.get(SECURITY_V2_ROLE_ROLE.ROLE_ID), Collectors
                        .mapping(rec -> rec.into(RoleV2.class), Collectors.toList())));

        return subRoles.flatMap(map -> Flux
                .from(this.dslContext.selectFrom(SECURITY_V2_ROLE)
                        .where(SECURITY_V2_ROLE.ID.in(roleIds)))
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

                (roles, permissions) -> Mono.<Map<String, List<String>>>just(

                        Stream.concat(
                                roles.stream()
                                        .map(e -> Tuples.of(
                                                CommonsUtil.nonNullValue(e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                                        ""),
                                                AuthoritiesNameUtil.makeRoleName(
                                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                                        e.getValue(SecurityV2Role.SECURITY_V2_ROLE.NAME)))),
                                permissions
                                        .stream()
                                        .map(e -> Tuples.of(
                                                CommonsUtil.nonNullValue(e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                                        ""),
                                                AuthoritiesNameUtil.makePermissionName(
                                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                                        e.getValue(SecurityPermission.SECURITY_PERMISSION.NAME)))))
                                .collect(Collectors.groupingBy(Tuple2::getT1,
                                        Collectors.mapping(Tuple2::getT2, Collectors.toList()))))

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "UserDAO.getRoleAuthorities"));
    }

    public Mono<List<RoleV2>> getRolesForAssignmentInApp(String appCode, ClientHierarchy hierarchy) {

        return Flux.from(this.dslContext.selectFrom(SECURITY_V2_ROLE).where(DSL.and(
                SECURITY_V2_ROLE.CLIENT_ID.eq(hierarchy.getClientId()),
                "nothing".equals(appCode) ? DSL.trueCondition()
                        : DSL.or(SECURITY_V2_ROLE.APP_ID.eq(this.dslContext
                                .select(SECURITY_APP.ID).from(SECURITY_APP)
                                .where(SECURITY_APP.APP_CODE.eq(appCode))),
                                SECURITY_V2_ROLE.APP_ID.isNull()))))
                .map(r -> r.into(RoleV2.class)).collectList();
    }

    public Mono<Map<ULong, List<RoleV2>>> fetchSubRoles(List<ULong> roleIds) {

        return Flux.from(this.dslContext.select(SECURITY_V2_ROLE.fields()).select(SECURITY_V2_ROLE_ROLE.ROLE_ID)
                .from(SECURITY_V2_ROLE_ROLE)
                .leftJoin(SECURITY_V2_ROLE)
                .on(SECURITY_V2_ROLE.ID.eq(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID))
                .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.in(roleIds))).collect(Collectors.groupingBy(
                        r -> r.get(SECURITY_V2_ROLE_ROLE.ROLE_ID),
                        Collectors.mapping(r -> r.into(RoleV2.class), Collectors.toList())));
    }
    // ── role to role ──────────────────────────────────────────────────────────
    //
    // `security_v2_role_role` is the sub-role tree, and until now nothing in Java
    // ever wrote to it: every row came from a Flyway migration or the seed SQL. It
    // carries no unique key on (ROLE_ID, SUB_ROLE_ID) either, so a caller that does
    // not check first gets a duplicate row rather than an error.

    private static final int MAX_ROLE_DEPTH = 32;

    public Mono<Boolean> hasSubRole(ULong roleId, ULong subRoleId) {

        return Mono.from(this.dslContext.selectCount()
                        .from(SECURITY_V2_ROLE_ROLE)
                        .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.eq(roleId)
                                .and(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID.eq(subRoleId))))
                .map(Record1::value1)
                .map(count -> count > 0);
    }

    public Mono<Boolean> addSubRole(ULong roleId, ULong subRoleId) {

        return Mono.from(this.dslContext
                        .insertInto(SECURITY_V2_ROLE_ROLE,
                                SECURITY_V2_ROLE_ROLE.ROLE_ID,
                                SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID)
                        .values(roleId, subRoleId))
                .map(value -> value > 0);
    }

    public Mono<Integer> removeSubRole(ULong roleId, ULong subRoleId) {

        return Mono.from(this.dslContext.delete(SECURITY_V2_ROLE_ROLE)
                .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.eq(roleId)
                        .and(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID.eq(subRoleId))));
    }

    public Mono<List<RoleV2>> fetchSubRolesOf(ULong roleId) {

        return Flux.from(this.dslContext.select(SECURITY_V2_ROLE.fields())
                        .from(SECURITY_V2_ROLE_ROLE)
                        .leftJoin(SECURITY_V2_ROLE)
                        .on(SECURITY_V2_ROLE.ID.eq(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID))
                        .where(SECURITY_V2_ROLE_ROLE.ROLE_ID.eq(roleId)))
                .map(r -> r.into(RoleV2.class))
                .collectList();
    }

    /** Every role reachable downwards from this one, itself included. */
    public Mono<Set<ULong>> descendantsOf(ULong roleId) {
        return this.closure(roleId, true);
    }

    /** Every role that reaches this one, itself included. */
    public Mono<Set<ULong>> ancestorsOf(ULong roleId) {
        return this.closure(roleId, false);
    }

    private Mono<Set<ULong>> closure(ULong start, boolean downwards) {

        Set<ULong> seen = new HashSet<>();
        seen.add(start);
        return this.expandClosure(Set.of(start), seen, downwards, 0);
    }

    private Mono<Set<ULong>> expandClosure(Set<ULong> frontier, Set<ULong> seen, boolean downwards, int depth) {

        // The tree is data, so a cycle is possible however wrong it is; `seen`
        // already stops one, and the depth cap stops a pathologically deep tree
        // from turning one edit into hundreds of queries.
        if (frontier.isEmpty() || depth >= MAX_ROLE_DEPTH)
            return Mono.just(seen);

        Field<ULong> from = downwards ? SECURITY_V2_ROLE_ROLE.ROLE_ID : SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID;
        Field<ULong> to = downwards ? SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID : SECURITY_V2_ROLE_ROLE.ROLE_ID;

        return Flux.from(this.dslContext.select(to).from(SECURITY_V2_ROLE_ROLE).where(from.in(frontier)))
                .map(Record1::value1)
                .collect(Collectors.toSet())
                .flatMap(next -> {
                    Set<ULong> fresh = next.stream().filter(id -> !seen.contains(id)).collect(Collectors.toSet());
                    seen.addAll(fresh);
                    return this.expandClosure(fresh, seen, downwards, depth + 1);
                });
    }

    /**
     * Profiles whose role list contains any of these roles. A sub-role edit changes
     * what those profiles grant, so their cached authorities have to go.
     */
    public Mono<Set<ULong>> profileIdsWithAnyRole(Collection<ULong> roleIds) {

        if (roleIds.isEmpty())
            return Mono.just(Set.of());

        // The authorities cache is named after the profile id the USER holds, which
        // for an inherited profile is its root, so both ids are collected and both
        // are evicted. Evicting one id too many costs a recompute; missing one
        // leaves a stale authority in place until the process restarts.
        return Flux.from(this.dslContext
                        .selectDistinct(SecurityProfileRole.SECURITY_PROFILE_ROLE.PROFILE_ID,
                                SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID)
                        .from(SecurityProfileRole.SECURITY_PROFILE_ROLE)
                        .leftJoin(SecurityProfile.SECURITY_PROFILE)
                        .on(SecurityProfile.SECURITY_PROFILE.ID
                                .eq(SecurityProfileRole.SECURITY_PROFILE_ROLE.PROFILE_ID))
                        .where(SecurityProfileRole.SECURITY_PROFILE_ROLE.ROLE_ID.in(roleIds)))
                .flatMap(r -> r.value2() == null ? Flux.just(r.value1()) : Flux.just(r.value1(), r.value2()))
                .collect(Collectors.toSet());
    }
    /**
     * App code per app id, for filling `appName` on a role. The DTO field is called
     * appName and carries the CODE: that is what identifies an app everywhere else
     * on the platform, and it is what the authority name is built from.
     */
    public Mono<Map<ULong, String>> appCodesOf(Collection<ULong> appIds) {

        if (appIds.isEmpty())
            return Mono.just(Map.of());

        return Flux.from(this.dslContext.select(SECURITY_APP.ID, SECURITY_APP.APP_CODE)
                        .from(SECURITY_APP)
                        .where(SECURITY_APP.ID.in(appIds)))
                .collect(Collectors.toMap(r -> r.value1(), r -> r.value2()));
    }
}
