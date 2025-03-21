package com.fincity.security.dao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.BooleanUtil;
import static com.fincity.saas.commons.util.CommonsUtil.safeEquals;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.ProfileArrangement;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityAppAccess;
import com.fincity.security.jooq.tables.SecurityPermission;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.SecurityProfileClientRestriction;
import com.fincity.security.jooq.tables.SecurityV2Role;
import com.fincity.security.jooq.tables.SecurityV2RolePermission;
import com.fincity.security.jooq.tables.SecurityV2RoleRole;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;
import com.google.common.base.Functions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class ProfileDAO extends AbstractClientCheckDAO<SecurityProfileRecord, ULong, Profile> {

        public ProfileDAO() {
                super(Profile.class, SecurityProfile.SECURITY_PROFILE, SecurityProfile.SECURITY_PROFILE.CLIENT_ID);
        }

        @Override
        public Field<ULong> getClientIDField() {
                return SecurityProfile.SECURITY_PROFILE.CLIENT_ID;
        }

        @Override
        public Mono<Profile> create(Profile entity) {
                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                                "Profile creation thru DAO is not allowed"));
        }

        @Override
        public Mono<Profile> readById(ULong profileId) {
                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Profile read thru DAO is not allowed"));
        }

        public Mono<Profile> create(Profile entity, ClientHierarchy clientHierarchy) {

                List<ULong> roleIds = entity.getArrangements().stream().map(ProfileArrangement::getRoleId)
                                .filter(Objects::nonNull).toList();

                return FlatMapUtil.flatMapMono(
                                () -> this.hasAccessToRoles(clientHierarchy, roleIds),
                                hasAccess -> {
                                        if (!BooleanUtil.safeValueOf(hasAccess)) {
                                                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                                                                "Profile has no access to roles"));
                                        }
                                        if (entity.getRootProfileId() == null)
                                                return Mono.just(entity);

                                        return this.readByRootProfileId(clientHierarchy, entity.getRootProfileId(),
                                                        true)
                                                        .flatMap(rootProfile -> this.difference(entity, rootProfile));
                                },
                                (hasAccess, profile) -> super.create(profile),

                                (hasAccess, profile, created) -> this.createArrangements(profile,
                                                entity.getArrangements()),

                                (hasAccess, profile, created, arrangements) -> this.readById(created.getId(),
                                                clientHierarchy)

                ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.create"));
        }

        public Mono<Profile> difference(Profile profile, Profile rootProfile) {

                if (!safeEquals(profile.getAppId(), rootProfile.getAppId()))
                        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                                        "Profile app id does not match root profile app id"));

                if (safeEquals(profile.getName(), rootProfile.getName()))
                        profile.setName(null);

                if (safeEquals(profile.getDescription(), rootProfile.getDescription()))
                        profile.setDescription(null);

                List<ProfileArrangement> arrangements = this.difference(profile.getClientId(),
                                profile.getArrangements(),
                                rootProfile.getArrangements(), true);
                profile.setArrangements(arrangements);

                return Mono.just(profile);
        }

    public List<ProfileArrangement> difference(ULong clientId, List<ProfileArrangement> arrangements,
            List<ProfileArrangement> baseArrangements, boolean forCreate) {

        // Need to compare both the profile arrangements and base arrangements
        // If there is any difference add that to the list of arrangements to return
        // If forCreate is true, all the arrangements with id should be checked for the
        // change and anything without id should be added.
        // If forCreate is false, all the arrangements with id should be checked for the
        // change and anything without id should be removed.

        Map<ULong, ProfileArrangement> baseMap = baseArrangements.stream()
                .collect(Collectors.toMap(ProfileArrangement::getId, Function.identity()));
        Set<ULong> keysUsed = new LinkedHashSet<>();

        List<ProfileArrangement> result = new ArrayList<>();

        for (ProfileArrangement arrangement : arrangements) {

            if (arrangement.getId() == null) {
                arrangement.setClientId(clientId);
                arrangement.setSubArrangements(
                        this.difference(clientId, arrangement.getSubArrangements(), List.of(),
                                forCreate));
                result.add(arrangement);
                continue;
            }

            ProfileArrangement baseArrangement = baseMap.get(arrangement.getId());
            if (baseArrangement == null && arrangement.getOverrideArrangementId() != null
                    && safeEquals(arrangement.getClientId(), clientId)) {
                baseArrangement = baseMap.get(arrangement.getOverrideArrangementId());
            }
            if (baseArrangement == null) {
                arrangement.setClientId(clientId);
                arrangement.setId(null);
                arrangement.setSubArrangements(
                        this.difference(clientId, arrangement.getSubArrangements(), List.of(),
                                forCreate));
                result.add(arrangement);
                continue;
            }

            keysUsed.add(baseArrangement.getId());
            if (

            result.add(arrangement);
        }

        return result;
    }

        public Mono<List<ProfileArrangement>> createArrangements(Profile profile,
                        List<ProfileArrangement> arrangements) {
                return Mono.just(arrangements);
        }

        public Mono<Boolean> hasAccessToRoles(ClientHierarchy clientHierarchy, List<ULong> roleIds) {
                return this.hasAccessToRoles(null, clientHierarchy, roleIds);
        }

        public Mono<Boolean> hasAccessToRoles(ULong appId, ClientHierarchy clientHierarchy, List<ULong> roleIds) {

                Mono<List<ULong>> restrictionsList = Flux.from(
                                this.dslContext.select(
                                                SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                                                .from(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION)
                                                .where(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
                                                                .eq(clientHierarchy.getClientId())))
                                .map(r -> r.into(ULong.class)).collectList().defaultIfEmpty(List.of());

                return FlatMapUtil.flatMapMono(
                                () -> restrictionsList,
                                restrictions -> {

                                        SelectConditionStep<Record1<ULong>> profileIDQuery;

                                        if (!restrictions.isEmpty()) {
                                                profileIDQuery = this.dslContext
                                                                .select(SecurityProfile.SECURITY_PROFILE.ID)
                                                                .from(SecurityProfile.SECURITY_PROFILE)
                                                                .where(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                                                                .in(restrictions));
                                        } else if (appId != null) {
                                                profileIDQuery = this.dslContext
                                                                .select(SecurityProfile.SECURITY_PROFILE.ID)
                                                                .from(SecurityProfile.SECURITY_PROFILE)
                                                                .where(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                                                                .in(clientHierarchy.getClientIds())
                                                                                .and(SecurityProfile.SECURITY_PROFILE.APP_ID
                                                                                                .eq(appId)));
                                        } else {
                                                var createAppIds = this.dslContext.select(SecurityApp.SECURITY_APP.ID)
                                                                .from(SecurityApp.SECURITY_APP)
                                                                .where(SecurityApp.SECURITY_APP.CLIENT_ID
                                                                                .eq(clientHierarchy.getClientId()));

                                                var accessAppIds = this.dslContext
                                                                .select(SecurityAppAccess.SECURITY_APP_ACCESS.APP_ID)
                                                                .from(SecurityAppAccess.SECURITY_APP_ACCESS)
                                                                .where(SecurityAppAccess.SECURITY_APP_ACCESS.CLIENT_ID
                                                                                .eq(clientHierarchy.getClientId()));

                                                var appIds = createAppIds.union(accessAppIds);

                                                profileIDQuery = this.dslContext
                                                                .select(SecurityProfile.SECURITY_PROFILE.ID)
                                                                .from(SecurityProfile.SECURITY_PROFILE)
                                                                .where(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                                                                .in(clientHierarchy.getClientIds())
                                                                                .and(SecurityProfile.SECURITY_PROFILE.APP_ID
                                                                                                .in(appIds)));
                                        }

                                        SelectConditionStep<Record1<ULong>> roleIDQuery = this.dslContext
                                                        .selectDistinct(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID)
                                                        .from(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT)
                                                        .where(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID
                                                                        .in(profileIDQuery)
                                                                        .and(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID
                                                                                        .isNotNull()));

                                        SelectConditionStep<Record1<ULong>> roleIDNotInQuery = this.dslContext
                                                        .selectDistinct(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID)
                                                        .from(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT)
                                                        .where(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID
                                                                        .in(profileIDQuery)
                                                                        .and(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID
                                                                                        .notIn(roleIds)));

                                        SelectConditionStep<Record1<ULong>> subRoleIDQuery = this.dslContext
                                                        .selectDistinct(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID)
                                                        .from(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE)
                                                        .where(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.ROLE_ID
                                                                        .in(roleIDQuery).and(
                                                                                        SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID
                                                                                                        .notIn(roleIds)));

                                        return Mono.from(this.dslContext.selectCount().from(
                                                        roleIDNotInQuery.union(subRoleIDQuery)))
                                                        .map(r -> r.into(Integer.class))
                                                        .defaultIfEmpty(0).map(count -> count == 0);
                                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.hasAccessToRoles"));
        }

        public Mono<Profile> readById(ULong profileId, ClientHierarchy clientHierarchy) {
                return FlatMapUtil.flatMapMono(
                                () -> super.readById(profileId),
                                profile -> {
                                        if (profile.getRootProfileId() == null) {
                                                return this.fillProfile(profile);
                                        }
                                        return this.fillProfileWithRootProfile(clientHierarchy, profile);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.readById"));
        }

        private Mono<Profile> fillProfile(Profile profile) {
                return FlatMapUtil.flatMapMono(
                                () -> Flux.from(this.dslContext.select(
                                                SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ID,
                                                SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID,
                                                SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID,
                                                DSL.coalesce(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.NAME,
                                                                SecurityV2Role.SECURITY_V2_ROLE.NAME).as("NAME"),
                                                DSL.coalesce(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.DESCRIPTION,
                                                                SecurityV2Role.SECURITY_V2_ROLE.DESCRIPTION)
                                                                .as("DESCRIPTION"),
                                                DSL.coalesce(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.SHORT_NAME,
                                                                SecurityV2Role.SECURITY_V2_ROLE.SHORT_NAME)
                                                                .as("SHORT_NAME"),
                                                SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ASSIGNABLE,
                                                SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ORDER)
                                                .from(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT)
                                                .leftJoin(SecurityV2Role.SECURITY_V2_ROLE)
                                                .on(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ROLE_ID
                                                                .eq(SecurityV2Role.SECURITY_V2_ROLE.ID))
                                                .where(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID
                                                                .eq(profile.getId()))
                                                .orderBy(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.ORDER
                                                                .asc()))
                                                .map(r -> r.into(ProfileArrangement.class))
                                                .collectList(),

                                arrangements -> this.fillArrangements(profile, arrangements))
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.fillProfile"));
        }

        private Mono<Profile> fillArrangements(Profile profile, List<ProfileArrangement> arrangements) {
                return FlatMapUtil.flatMapMono(
                                () -> Flux.from(
                                                this.dslContext.select(SecurityV2Role.SECURITY_V2_ROLE.fields())
                                                                .select(SecurityApp.SECURITY_APP.APP_NAME
                                                                                .as("APP_NAME"))
                                                                .from(SecurityV2Role.SECURITY_V2_ROLE)
                                                                .leftJoin(SecurityApp.SECURITY_APP)
                                                                .on(SecurityV2Role.SECURITY_V2_ROLE.APP_ID
                                                                                .eq(SecurityApp.SECURITY_APP.ID))
                                                                .where(SecurityV2Role.SECURITY_V2_ROLE.ID
                                                                                .in(arrangements.stream().map(
                                                                                                ProfileArrangement::getRoleId)
                                                                                                .toList())))
                                                .map(r -> r.into(RoleV2.class)).collectList(),

                                roles -> Flux.from(
                                                this.dslContext.select(SecurityV2Role.SECURITY_V2_ROLE.fields())
                                                                .select(SecurityApp.SECURITY_APP.APP_NAME
                                                                                .as("APP_NAME"))
                                                                .select(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.ROLE_ID
                                                                                .as("PARENT_ROLE_ID"))
                                                                .from(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE)
                                                                .leftJoin(SecurityV2Role.SECURITY_V2_ROLE)
                                                                .on(SecurityV2Role.SECURITY_V2_ROLE.ID
                                                                                .eq(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID))
                                                                .leftJoin(SecurityApp.SECURITY_APP)
                                                                .on(SecurityV2Role.SECURITY_V2_ROLE.APP_ID
                                                                                .eq(SecurityApp.SECURITY_APP.ID))
                                                                .where(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.ROLE_ID
                                                                                .in(roles.stream().map(RoleV2::getId)
                                                                                                .toList())))
                                                .map(r -> r.into(RoleV2.class)).collectList(),

                                (roles, subRoles) -> Flux.from(
                                                this.dslContext.select(SecurityPermission.SECURITY_PERMISSION.fields())
                                                                .select(SecurityApp.SECURITY_APP.APP_NAME
                                                                                .as("APP_NAME"))
                                                                .select(SecurityV2Role.SECURITY_V2_ROLE.ID
                                                                                .as("ROLE_ID"))
                                                                .from(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION)
                                                                .leftJoin(SecurityPermission.SECURITY_PERMISSION)
                                                                .on(SecurityV2Role.SECURITY_V2_ROLE.APP_ID
                                                                                .eq(SecurityApp.SECURITY_APP.ID))
                                                                .where(SecurityV2Role.SECURITY_V2_ROLE.ID
                                                                                .in(Stream.concat(
                                                                                                arrangements.stream()
                                                                                                                .map(ProfileArrangement::getRoleId),
                                                                                                subRoles.stream().map(
                                                                                                                RoleV2::getId))
                                                                                                .toList())))
                                                .map(r -> r.into(Permission.class)).collectList(),

                                (roles, subRoles, permissions) -> {

                                        Map<ULong, RoleV2> roleMap = roles.stream()
                                                        .collect(Collectors.toMap(RoleV2::getId, Function.identity()));
                                        Map<ULong, List<Permission>> rolePermissionsMap = permissions.stream()
                                                        .collect(Collectors.groupingBy(Permission::getRoleId));
                                        Map<ULong, List<RoleV2>> roleSubRolesMap = subRoles.stream()
                                                        .collect(Collectors.groupingBy(RoleV2::getParentRoleId));
                                        Map<ULong, ProfileArrangement> arrangementMap = arrangements.stream()
                                                        .collect(Collectors.toMap(ProfileArrangement::getId,
                                                                        Function.identity()));

                                        for (ProfileArrangement arrangement : arrangements) {
                                                RoleV2 role = roleMap.get(arrangement.getRoleId());
                                                role.setPermissions(rolePermissionsMap.get(role.getId()));
                                                role.setSubRoles(roleSubRolesMap.get(role.getId()));
                                                arrangement.setRole(role);
                                        }

                                        List<ProfileArrangement> sortedArrangements = arrangements.stream()
                                                        .sorted(Comparator.comparing(ProfileArrangement::getOrder))
                                                        .toList();

                                        List<ProfileArrangement> topLevelArrangements = new ArrayList<>();

                                        for (ProfileArrangement arrangement : sortedArrangements) {

                                                ProfileArrangement parentArrangement = arrangementMap
                                                                .get(arrangement.getParentArrangementId());
                                                if (parentArrangement == null) {
                                                        topLevelArrangements.add(arrangement);
                                                        continue;
                                                }

                                                if (parentArrangement.getSubArrangements() == null)
                                                        parentArrangement.setSubArrangements(new ArrayList<>());

                                                parentArrangement.getSubArrangements().add(arrangement);
                                        }

                                        profile.setArrangements(topLevelArrangements);
                                        return Mono.just(profile);
                                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.fillArrangements"));
        }

        private Mono<Profile> fillProfileWithRootProfile(ClientHierarchy clientHierarchy, Profile profile) {

                return FlatMapUtil.flatMapMono(
                                () -> Flux.from(this.dslContext.selectFrom(SecurityProfile.SECURITY_PROFILE)
                                                .where(
                                                                DSL.or(
                                                                                SecurityProfile.SECURITY_PROFILE.ID.eq(
                                                                                                profile.getRootProfileId()),
                                                                                SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID
                                                                                                .eq(profile.getRootProfileId())
                                                                                                .and(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                                                                                                .in(clientHierarchy
                                                                                                                                .getManagingClientIds())))))
                                                .map(r -> r.into(Profile.class)).collectList(),

                                profileList -> {
                                        List<Profile> finList = new ArrayList<>(profileList);
                                        List<ULong> pref = clientHierarchy.getClientIdsInOrder().reversed();
                                        finList.add(profile);

                                        finList.sort(Comparator.comparingInt(p -> pref.indexOf(p.getClientId())));

                                        return this.fillProfileWithOverride(finList);
                                })
                                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.fillProfileWithRootProfile"));
        }

        private Mono<Profile> fillProfileWithOverride(List<Profile> profiles) {

                if (profiles.isEmpty())
                        return Mono.empty();

                return FlatMapUtil.flatMapMono(
                                () -> Flux.from(
                                                this.dslContext.selectFrom(
                                                                SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT)
                                                                .where(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT.PROFILE_ID
                                                                                .in(profiles.stream()
                                                                                                .map(Profile::getId)
                                                                                                .toList())))
                                                .map(r -> r.into(ProfileArrangement.class)).collectList(),

                                arrangements -> {
                                        Map<ULong, ProfileArrangement> arrangementMap = arrangements.stream()
                                                        .collect(Collectors.toMap(ProfileArrangement::getId,
                                                                        Functions.identity()));

                                        Map<ULong, List<ProfileArrangement>> clientGroup = arrangements.stream()
                                                        .collect(Collectors
                                                                        .groupingBy(ProfileArrangement::getClientId));

                                        Profile base = profiles.getFirst();

                                        List<ProfileArrangement> finalArrangements = new ArrayList<>(
                                                        clientGroup.get(base.getClientId()));

                                        for (int i = 1; i < profiles.size(); i++) {
                                                Profile p = profiles.get(i);

                                                if (p.getName() != null)
                                                        base.setName(p.getName());
                                                if (p.getDescription() != null)
                                                        base.setDescription(p.getDescription());
                                                base.setClientId(p.getClientId());

                                                for (ProfileArrangement pa : clientGroup.get(p.getClientId())) {
                                                        if (pa.getOverrideArrangementId() == null) {
                                                                finalArrangements.add(pa);
                                                                continue;
                                                        }

                                                        ProfileArrangement ba = arrangementMap
                                                                        .get(pa.getOverrideArrangementId());
                                                        if (ba == null)
                                                                continue;

                                                        if (pa.getRoleId() != null)
                                                                ba.setRoleId(pa.getRoleId());
                                                        if (pa.getName() != null)
                                                                ba.setName(pa.getName());
                                                        if (pa.getShortName() != null)
                                                                ba.setShortName(pa.getShortName());
                                                        if (pa.getDescription() != null)
                                                                ba.setDescription(pa.getDescription());
                                                        if (pa.getAssignable() != null)
                                                                ba.setAssignable(pa.getAssignable());
                                                        if (pa.getOrder() != null)
                                                                ba.setOrder(pa.getOrder());
                                                        if (pa.getParentArrangementId() != null)
                                                                ba.setParentArrangementId(pa.getParentArrangementId());
                                                }
                                        }

                                        return this.fillArrangements(base, finalArrangements);
                                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.fillProfileWithOverride"));
        }

        public Mono<Profile> readByRootProfileId(ClientHierarchy clientHierarchy, ULong profileId,
                        boolean ignoreClientId) {

                return FlatMapUtil.flatMapMono(
                                () -> Flux.from(this.dslContext.selectFrom(SecurityProfile.SECURITY_PROFILE)
                                                .where(
                                                                DSL.or(
                                                                                SecurityProfile.SECURITY_PROFILE.ID
                                                                                                .eq(profileId),
                                                                                SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID
                                                                                                .eq(profileId)
                                                                                                .and(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                                                                                                .in(ignoreClientId
                                                                                                                                ? clientHierarchy
                                                                                                                                                .getManagingClientIds()
                                                                                                                                : clientHierarchy
                                                                                                                                                .getClientIds())))))
                                                .map(r -> r.into(Profile.class)).collectList(),

                                profileList -> {
                                        List<Profile> finList = new ArrayList<>(profileList);
                                        List<ULong> pref = clientHierarchy.getClientIdsInOrder().reversed();

                                        finList.sort(Comparator.comparingInt(p -> pref.indexOf(p.getClientId())));

                                        return this.fillProfileWithOverride(finList);
                                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.readByRootProfileId"));
        }
}