package com.fincity.security.dao;

import static com.fincity.saas.commons.util.StringUtil.*;

import java.util.*;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.records.SecurityProfileRoleRecord;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.ByteUtil;
import com.fincity.saas.commons.util.DifferenceApplicator;
import com.fincity.saas.commons.util.DifferenceExtractor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Profile;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityProfileUser.SECURITY_PROFILE_USER;

import com.fincity.security.jooq.tables.SecurityAppAccess;
import com.fincity.security.jooq.tables.SecurityClientHierarchy;
import com.fincity.security.jooq.tables.SecurityPermission;

import static com.fincity.security.jooq.tables.SecurityProfile.SECURITY_PROFILE;
import static com.fincity.security.jooq.tables.SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION;

import static com.fincity.security.jooq.tables.SecurityProfileRole.SECURITY_PROFILE_ROLE;

import com.fincity.security.jooq.tables.SecurityProfileUser;

import static com.fincity.security.jooq.tables.SecurityV2Role.SECURITY_V2_ROLE;

import com.fincity.security.jooq.tables.SecurityV2RolePermission;

import static com.fincity.security.jooq.tables.SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE;

import com.fincity.security.jooq.tables.records.SecurityProfileRecord;
import com.fincity.security.util.AuthoritiesNameUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Component
public class ProfileDAO extends AbstractClientCheckDAO<SecurityProfileRecord, ULong, Profile> {

    public ProfileDAO() {
        super(Profile.class, SECURITY_PROFILE, SECURITY_PROFILE.ID);
    }

    @Override
    public Field<ULong> getClientIDField() {
        return SECURITY_PROFILE.CLIENT_ID;
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

    @Override
    public <A extends AbstractUpdatableDTO<ULong, ULong>> Mono<Profile> update(A entity) {

        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                "Profile update thru DAO is not allowed"));
    }

    @Override
    public Mono<Profile> update(ULong id, Map<String, Object> updateFields) {
        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                "Profile update thru DAO is not allowed"));
    }

    @Override
    public Mono<Integer> delete(ULong profileId) {
        return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                "Profile deletion thru DAO is not allowed"));
    }

    public Mono<Profile> read(ULong id, ClientHierarchy hierarchy) {

        return FlatMapUtil.flatMapMono(
                        () -> super.readById(id),
                        profile -> {
                            if (profile.getRootProfileId() == null
                                    && hierarchy.getClientId().equals(profile.getClientId()))
                                return Mono.just(profile);
                            else
                                return this.readRootProfile(
                                        profile.getRootProfileId() == null ? profile.getId()
                                                : profile.getRootProfileId(),
                                        hierarchy, true);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.read"));
    }

    @SuppressWarnings("unchecked")
    public Mono<Profile> readRootProfile(ULong id, ClientHierarchy hierarchy, boolean includeClientId) {

        return FlatMapUtil.flatMapMono(

                        () -> Flux.from(this.dslContext.selectFrom(SECURITY_PROFILE)
                                        .where(DSL.or(
                                                SECURITY_PROFILE.ID.eq(id),
                                                SECURITY_PROFILE.ROOT_PROFILE_ID.eq(id)
                                                        .and(SECURITY_PROFILE.CLIENT_ID
                                                                .in(includeClientId
                                                                        ? hierarchy.getClientIds()
                                                                        : hierarchy.getManagingClientIds())))))
                                .map(r -> r.into(Profile.class)).collectList(),

                        profiles -> {

                            Profile base = profiles.stream().filter(e -> e.getRootProfileId() == null)
                                    .findFirst().orElse(null);
                            if (base == null)
                                return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
                                        "Root profile not found"));

                            Map<ULong, Integer> clientPref = hierarchy.getClientOrder();
                            base.setRootProfileId(base.getId());

                            return Flux.fromStream(profiles.stream())
                                    .sort(Comparator.comparingInt((Profile e) -> clientPref
                                                    .getOrDefault(e.getClientId(), -1))
                                            .reversed())
                                    .filter(profile -> !profile.getId().equals(base.getId()))
                                    .flatMap(profile -> {
                                        base.setId(profile.getId());
                                        base.setClientId(profile.getClientId());

                                        if (!safeIsBlank(profile.getName()))
                                            base.setName(profile.getName());

                                        if (!safeIsBlank(profile.getDescription()))
                                            base.setDescription(profile.getDescription());

                                        return DifferenceApplicator.apply(profile.getArrangement(), base.getArrangement())
                                                .map(x -> base.setArrangement((Map<String, Object>) x));
                                    }).collectList().map(x -> base);
                        }
                ).subscribeOn(Schedulers.boundedElastic())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.readRootProfile"));
    }

    public Mono<Profile> createUpdateProfile(Profile profile, ULong userId, ClientHierarchy hierarchy) {

        if (profile.getId() == null || !hierarchy.getClientId().equals(profile.getClientId())) {
            if (profile.getId() != null) {
                profile.setRootProfileId(profile.getId());
            }
            profile.setId(null);
            profile.setCreatedBy(userId);
            return create(profile, hierarchy);
        }

        profile.setUpdatedBy(userId);
        return update(profile, hierarchy);
    }

    @SuppressWarnings("unchecked")
    private Mono<Profile> create(Profile profile, ClientHierarchy hierarchy) {

        Map<String, Object> arrangements = profile.getArrangement();

        if (profile.getRootProfileId() == null) {
            return FlatMapUtil.flatMapMono(
                            () -> super.create(profile),

                            created -> this
                                    .createRoleRelations(created.getId(),
                                            this.getRoleIdsFromArrangements(arrangements)
                                                    .collect(Collectors.toSet()),
                                            Set.of())
                                    .flatMap(e -> this.read(created.getId(), hierarchy)))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME,
                            "ProfileDao.create without rootProfileId"));
        }

        return FlatMapUtil.flatMapMono(

                        () -> this.readRootProfile(profile.getRootProfileId(), hierarchy, false),

                        rootProfile -> {

                            if (safeEquals(profile.getName(), rootProfile.getName()))
                                profile.setName(null);

                            if (safeEquals(profile.getDescription(), rootProfile.getDescription()))
                                profile.setDescription(null);

                            return DifferenceExtractor.extract(profile.getArrangement(),
                                    rootProfile.getArrangement());
                        },

                        (rootProfile, diff) -> {
                            profile.setArrangement((Map<String, Object>) diff);
                            return super.create(profile);
                        },

                        (rootProfile, diff, created) ->
                                this.prepAndCreateRoleRelations(rootProfile, created, arrangements)
                                        .<Profile>flatMap(e -> this.read(created.getId(), hierarchy))
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.create with rootProfileId"));
    }

    private Mono<Integer> prepAndCreateRoleRelations(Profile rootProfile, Profile created, Map<String, Object> arrangements) {

        Set<ULong> roleIds = this.getRoleIdsFromArrangements(arrangements)
                .collect(Collectors.toSet());
        Set<ULong> rootsRoleIds = this
                .getRoleIdsFromArrangements(rootProfile.getArrangement())
                .collect(Collectors.toSet());

        Set<ULong> newRoles = roleIds.stream().filter(r -> !rootsRoleIds.contains(r))
                .collect(Collectors.toSet());
        Set<ULong> removedRoles = rootsRoleIds.stream()
                .filter(r -> !roleIds.contains(r))
                .collect(Collectors.toSet());

        return this.createRoleRelations(created.getId(), newRoles, removedRoles);
    }

    private Mono<Integer> createRoleRelations(ULong profileId, Set<ULong> newRoles, Set<ULong> removedRoles) {

        var query = this.dslContext.insertInto(SECURITY_PROFILE_ROLE).columns(
                SECURITY_PROFILE_ROLE.PROFILE_ID,
                SECURITY_PROFILE_ROLE.ROLE_ID,
                SECURITY_PROFILE_ROLE.EXCLUDE
        );
        InsertValuesStep3<SecurityProfileRoleRecord, ULong, ULong, Byte> vQuery = null;

        for (ULong roleId : newRoles) {
            vQuery = Objects.requireNonNullElse(vQuery, query).values(profileId, roleId, ByteUtil.ZERO);
        }

        for (ULong roleId : removedRoles) {
            vQuery = Objects.requireNonNullElse(vQuery, query).values(profileId, roleId, ByteUtil.ONE);
        }

        if (vQuery == null) {
            return Mono.from(this.dslContext.deleteFrom(SECURITY_PROFILE_ROLE).where(SECURITY_PROFILE_ROLE.PROFILE_ID.eq(profileId)));
        }

        InsertValuesStep3<SecurityProfileRoleRecord, ULong, ULong, Byte> finVQuery = vQuery;

        return FlatMapUtil.flatMapMono(
                () -> Mono.from(this.dslContext.deleteFrom(SECURITY_PROFILE_ROLE).where(SECURITY_PROFILE_ROLE.PROFILE_ID.eq(profileId))),

                deleted -> Mono.from(finVQuery.onDuplicateKeyIgnore())
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.createRoleRelations"));
    }

    @SuppressWarnings({"unchecked"})
    private Stream<ULong> getRoleIdsFromArrangements(Map<String, Object> arrangements) {

        return arrangements.values().stream().flatMap(e -> {

            if (e instanceof Map<?, ?> m) {
                Object roleId = m.get("roleId");
                ULong rId = null;
                if (roleId != null && !Boolean.FALSE.equals(m.get("assignable"))) {
                    rId = ULong.valueOf(roleId.toString());
                }

                Object subArrangements = m.get("subArrangements");
                if (subArrangements instanceof Map<?, ?> m1) {

                    Stream<ULong> stream = this.getRoleIdsFromArrangements((Map<String, Object>) m1);
                    if (rId == null)
                        return stream;
                    else
                        return Stream.concat(Stream.of(rId), stream);
                }

                if (rId != null)
                    return Stream.of(rId);
            }

            return Stream.empty();
        });
    }

    @SuppressWarnings("unchecked")
    private Mono<Profile> update(Profile profile, ClientHierarchy hierarchy) {

        Map<String, Object> arrangements = profile.getArrangement();

        if (profile.getRootProfileId() == null) {

            return FlatMapUtil.flatMapMono(

                            () -> Mono.from(this.dslContext.update(SECURITY_PROFILE)
                                    .set(SECURITY_PROFILE.NAME, profile.getName())
                                    .set(SECURITY_PROFILE.DESCRIPTION,
                                            profile.getDescription())
                                    .set(SECURITY_PROFILE.ARRANGEMENT,
                                            profile.getArrangement())
                                    .where(SECURITY_PROFILE.ID
                                            .eq(profile.getId()))),

                            updated -> this
                                    .createRoleRelations(profile.getId(),
                                            this.getRoleIdsFromArrangements(arrangements)
                                                    .collect(Collectors.toSet()),
                                            Set.of())
                                    .flatMap(e -> this.read(profile.getId(), hierarchy)))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME,
                            "ProfileDao.update without rootProfileId"));
        }

        return FlatMapUtil.flatMapMono(

                        () -> this.readRootProfile(profile.getRootProfileId(), hierarchy, false),

                        rootProfile -> {

                            if (safeEquals(profile.getName(), rootProfile.getName()))
                                profile.setName(null);

                            if (safeEquals(profile.getDescription(), rootProfile.getDescription()))
                                profile.setDescription(null);

                            return DifferenceExtractor.extract(profile.getArrangement(),
                                    rootProfile.getArrangement());
                        },

                        (rootProfile, diff) -> {
                            profile.setArrangement((Map<String, Object>) diff);
                            return super.update(profile);
                        },

                        (rootProfile, diff, updated) ->
                                this.prepAndCreateRoleRelations(rootProfile, updated, arrangements)
                                        .<Profile>flatMap(e -> this.read(updated.getId(), hierarchy))
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.update with rootProfileId"));
    }

    public Mono<Integer> delete(ULong profileId, ClientHierarchy hierarchy) {
        return FlatMapUtil.flatMapMono(
                        () -> this.read(profileId, hierarchy),

                        profile -> Mono.from(
                                        this.dslContext.selectCount().from(SECURITY_PROFILE)
                                                .where(SECURITY_PROFILE.ROOT_PROFILE_ID
                                                        .eq(profile.getId())))
                                .map(Record1::value1)
                                .map(count -> count == 0)
                                .filter(BooleanUtil::safeValueOf),

                        (profile, notUsed) -> super.delete(profile.getId())

                ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.delete"))
                .defaultIfEmpty(0);
    }

    public Mono<Boolean> hasAccessToRoles(ULong appId, ClientHierarchy hierarchy, Set<ULong> roleIds) {

        Mono<List<ULong>> profileIds = Flux.from(this.dslContext.select(SECURITY_PROFILE.ID)
                        .from(SECURITY_PROFILE)
                        .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                        .where(DSL.and(SECURITY_PROFILE.APP_ID.eq(appId),
                                DSL.or(
                                        SECURITY_PROFILE.CLIENT_ID.in(hierarchy.getClientIds()),
                                        SECURITY_PROFILE.CLIENT_ID.eq(SECURITY_APP.CLIENT_ID)
                                )
                        )))
                .map(Record1::value1)
                .collectList();

        Mono<List<ULong>> restrictedProfiles = Flux.from(this.dslContext
                        .select(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                        .from(SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID))
                        .where(DSL.and(SECURITY_PROFILE.APP_ID
                                        .eq(appId),
                                SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
                                        .eq(hierarchy.getClientId()))))
                .map(Record1::value1)
                .collectList();

        return FlatMapUtil.flatMapMono(

                () -> restrictedProfiles,

                restrictedProfileIds -> restrictedProfileIds.isEmpty() ? profileIds
                        : Mono.just(restrictedProfileIds),

                (restrictedProfileIds, ids) -> {
                    var rolesInProfile = this.dslContext.selectDistinct(SECURITY_PROFILE_ROLE.ROLE_ID)
                            .from(SECURITY_PROFILE_ROLE)
                            .where(DSL.and(SECURITY_PROFILE_ROLE.PROFILE_ID.in(ids), SECURITY_PROFILE_ROLE.ROLE_ID.in(roleIds)));

                    var subRolesOfRolesInProfile = this.dslContext.selectDistinct(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID)
                            .from(SECURITY_V2_ROLE_ROLE)
                            .leftJoin(SECURITY_PROFILE_ROLE).on(SECURITY_PROFILE_ROLE.ROLE_ID.eq(SECURITY_V2_ROLE_ROLE.ROLE_ID))
                            .where(SECURITY_PROFILE_ROLE.PROFILE_ID.in(ids));

                    return Flux
                            .from(rolesInProfile.union(subRolesOfRolesInProfile))
                            .map(Record1::value1)
                            .collect(Collectors.toSet())
                            .map(set -> roleIds.stream().filter(Predicate.not(set::contains)).toList());
                },

                (restrictedProfileIds, ids, remainingRoleIds) -> {
                    if (remainingRoleIds.isEmpty())
                        return Mono.just(true);

                    return Mono.from(this.dslContext.selectCount()
                                    .from(SECURITY_V2_ROLE)
                                    .where(DSL.and(
                                            SECURITY_V2_ROLE.ID
                                                    .in(remainingRoleIds),
                                            DSL.or(
                                                    SECURITY_V2_ROLE.APP_ID
                                                            .eq(appId),
                                                    SECURITY_V2_ROLE.APP_ID
                                                            .isNull()),
                                            SECURITY_V2_ROLE.CLIENT_ID
                                                    .eq(hierarchy.getClientId()))))
                            .map(Record1::value1)
                            .map(count -> count == remainingRoleIds.size());
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.hasAccessToRoles"));
    }

    public Mono<Boolean> hasAccessToRoles(ULong appId, ClientHierarchy hierarchy, Profile profile) {
        return this.hasAccessToRoles(appId, hierarchy,
                this.getRoleIdsFromArrangements(profile.getArrangement()).collect(Collectors.toSet()));
    }

    public Mono<Page<Profile>> readAll(ULong appId, ClientHierarchy hierarchy, Pageable pageable) {

        Mono<List<ULong>> restrictedProfiles = Flux.from(this.dslContext
                        .select(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                        .from(SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID))
                        .where(DSL.and(SECURITY_PROFILE.APP_ID
                                        .eq(appId),
                                SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
                                        .eq(hierarchy.getClientId()))))
                .map(Record1::value1)
                .collectList();

        Mono<List<ULong>> profiles = Flux
                .from(this.dslContext
                        .select(SECURITY_PROFILE.ID, SECURITY_PROFILE.ROOT_PROFILE_ID)
                        .from(SECURITY_PROFILE)
                        .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                        .where(DSL.and(SECURITY_PROFILE.APP_ID.eq(appId),
                                DSL.or(
                                        SECURITY_PROFILE.CLIENT_ID.in(hierarchy.getClientIds()),
                                        SECURITY_PROFILE.CLIENT_ID.eq(SECURITY_APP.CLIENT_ID)
                                ))))
                .map(e -> e.value2() == null ? e.value1() : e.value2())
                .distinct()
                .collectList();

        return FlatMapUtil.flatMapMono(

                        () -> restrictedProfiles.flatMap(ids -> ids.isEmpty() ? profiles : Mono.just(ids)),

                        ids -> Flux
                                .fromStream(ids.stream().skip(pageable.getOffset())
                                        .limit(pageable.getPageSize()))
                                .flatMap(e -> this.read(e, hierarchy))
                                .collectList()
                                .<Page<Profile>>map(
                                        profilesList -> PageableExecutionUtils.getPage(
                                                profilesList, pageable,
                                                () -> (long) ids.size()))

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.readAll"));
    }

    public Mono<Boolean> checkProfileAppAccess(ULong profileId, ULong clientId) {

        return Mono.from(this.dslContext.selectCount().from(SECURITY_PROFILE)
                        .leftJoin(SecurityAppAccess.SECURITY_APP_ACCESS).on(
                                SECURITY_PROFILE.APP_ID
                                        .eq(SecurityAppAccess.SECURITY_APP_ACCESS.APP_ID))
                        .where(SECURITY_PROFILE.ID.eq(profileId)
                                .and(SecurityAppAccess.SECURITY_APP_ACCESS.CLIENT_ID.eq(clientId))))
                .map(Record1::value1)
                .map(count -> count > 0);
    }

    public Mono<Boolean> restrictClient(ULong profileId, ULong clientId) {
        return Mono
                .from(this.dslContext.insertInto(
                                SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .set(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID,
                                profileId)
                        .set(SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID,
                                clientId))
                .map(e -> e > 0);
    }

    public Mono<Boolean> hasAccessToProfiles(ULong clientId, Set<ULong> profileIds) {

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(
                                this.dslContext.select(SECURITY_APP.ID)
                                        .from(SECURITY_APP)
                                        .where(SECURITY_APP.CLIENT_ID.eq(clientId))
                                        .union(this.dslContext.select(
                                                        SecurityAppAccess.SECURITY_APP_ACCESS.APP_ID)
                                                .from(SecurityAppAccess.SECURITY_APP_ACCESS)
                                                .where(SecurityAppAccess.SECURITY_APP_ACCESS.CLIENT_ID
                                                        .eq(clientId))))
                        .map(Record1::value1)
                        .collectList().map(HashSet::new),

                appIds -> Flux.from(this.dslContext
                                .select(SECURITY_PROFILE.APP_ID,
                                        SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                                .from(SECURITY_PROFILE_CLIENT_RESTRICTION)
                                .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID))
                                .where(SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
                                        .eq(clientId)))
                        .collectMultimap(Record2::value1, Record2::value2),

                (appIds, restrictions) -> {

                    if (restrictions.isEmpty())
                        return Mono.just(Tuples.of(appIds, profileIds));

                    Set<ULong> processedAppIds = new HashSet<>(appIds);
                    Set<ULong> processedProfileIds = new HashSet<>(profileIds);

                    for (var entry : restrictions.entrySet()) {
                        processedAppIds.remove(entry.getKey());
                        processedProfileIds.removeAll(entry.getValue());
                    }

                    return Mono.just(Tuples.of(processedAppIds, processedProfileIds));
                },

                (appIds, restrictions, finalTuple) -> {

                    if (finalTuple.getT2().isEmpty())
                        return Mono.just(true);

                    return Mono.from(this.dslContext.selectCount()
                                    .from(SECURITY_PROFILE)
                                    .where(SECURITY_PROFILE.ID
                                            .in(finalTuple.getT2())
                                            .and(SECURITY_PROFILE.APP_ID
                                                    .notIn(finalTuple.getT1()))))
                            .map(Record1::value1)
                            .map(count -> count == 0);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.readAll"));
    }

    public Mono<Set<ULong>> getProfileIds(String appCode, ULong userId) {
        SelectConditionStep<Record1<ULong>> query = this.dslContext.select(SecurityProfileUser.SECURITY_PROFILE_USER.PROFILE_ID)
                .from(SecurityProfileUser.SECURITY_PROFILE_USER)
                .leftJoin(SECURITY_PROFILE)
                .on(SecurityProfileUser.SECURITY_PROFILE_USER.PROFILE_ID.eq(SECURITY_PROFILE.ID))
                .leftJoin(SECURITY_APP)
                .on(SECURITY_PROFILE.APP_ID.eq(SECURITY_APP.ID))
                .where(DSL.and(
                        SecurityProfileUser.SECURITY_PROFILE_USER.USER_ID.eq(userId),
                        appCode == null || appCode.equals("nothing") ? DSL.trueCondition() :
                                SECURITY_APP.APP_CODE.eq(appCode)));

        // If no profiles are assigned to the user in an app we shall search for default profiles.
        return Flux.from(query).map(Record1::value1)
                .collect(Collectors.toSet())
                .flatMap(e -> {
                    if (!e.isEmpty()) return Mono.just(e);

                    return Flux.from(this.dslContext.select(SECURITY_PROFILE.ID).from(SECURITY_PROFILE)
                                    .leftJoin(SECURITY_APP)
                                    .on(SECURITY_PROFILE.APP_ID.eq(SECURITY_APP.ID))
                                    .where(SECURITY_PROFILE.DEFAULT_PROFILE.eq(ByteUtil.ONE).and(SECURITY_APP.APP_CODE.eq(appCode))))
                            .map(Record1::value1)
                            .collect(Collectors.toSet());
                });
    }

    public Mono<Boolean> isBeingUsedByManagingClients(ULong clientId, ULong profileId, ULong rootProfileId) {

        ULong pId = rootProfileId == null ? profileId : rootProfileId;

        return Mono.from(this.dslContext.selectCount().from(SECURITY_PROFILE)
                        .leftJoin(SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY)
                        .on(SECURITY_PROFILE.CLIENT_ID
                                .eq(SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY.CLIENT_ID))
                        .where(
                                DSL.and(
                                        SECURITY_PROFILE.ROOT_PROFILE_ID.eq(pId),
                                        SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_0
                                                .eq(clientId),
                                        SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_1
                                                .eq(clientId),
                                        SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_2
                                                .eq(clientId),
                                        SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY.MANAGE_CLIENT_LEVEL_3
                                                .eq(clientId))))
                .map(Record1::value1).map(count -> count > 0);
    }

    public Mono<List<String>> getProfileAuthorities(ULong profileId, ClientHierarchy clientHierarchy) {

        return FlatMapUtil.flatMapMono(() -> Flux.from(this.dslContext.select(SECURITY_PROFILE.ID,
                                        SECURITY_PROFILE.NAME, SECURITY_APP.APP_CODE)
                                .from(SECURITY_PROFILE)
                                .leftJoin(SECURITY_APP)
                                .on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                                .where(
                                        DSL.or(
                                                SECURITY_PROFILE.ID.eq(profileId),
                                                DSL.and(
                                                        SECURITY_PROFILE.ROOT_PROFILE_ID.eq(profileId),
                                                        SECURITY_PROFILE.CLIENT_ID
                                                                .in(clientHierarchy.getClientIds())))

                                ))
                        .collectList(),

                profiles -> Flux.from(this.dslContext.select(
                                        SECURITY_V2_ROLE.ID,
                                        SECURITY_PROFILE.CLIENT_ID,
                                        SECURITY_PROFILE.ID,
                                        SECURITY_V2_ROLE.NAME,
                                        SECURITY_APP.APP_CODE,
                                        SECURITY_PROFILE_ROLE.EXCLUDE)
                                .from(SECURITY_PROFILE_ROLE)
                                .leftJoin(SECURITY_V2_ROLE)
                                .on(SECURITY_PROFILE_ROLE.ROLE_ID.eq(SECURITY_V2_ROLE.ID))
                                .leftJoin(SECURITY_APP)
                                .on(SECURITY_V2_ROLE.APP_ID.eq(SECURITY_APP.ID))
                                .leftJoin(SECURITY_PROFILE)
                                .on(SECURITY_PROFILE.ID
                                        .eq(SECURITY_PROFILE_ROLE.PROFILE_ID))
                                .where(SECURITY_PROFILE_ROLE.PROFILE_ID
                                        .in(profiles.stream().map(Record3::value1).collect(Collectors.toSet()))))

                        .collect(Collectors.groupingBy(e -> e.get(SECURITY_V2_ROLE.ID)))
                        .map(map -> {

                            List<Record6<ULong, ULong, ULong, String, String, Byte>> records = new ArrayList<>();

                            Map<ULong, Integer> pref = clientHierarchy.getClientOrder();

                            for (var entry : map.entrySet()) {
                                if (entry.getValue().size() == 1) {
                                    Byte bValue = entry.getValue().getFirst()
                                            .get(SECURITY_PROFILE_ROLE.EXCLUDE);
                                    if (bValue == null || bValue == 0) {
                                        records.add(entry.getValue().getFirst());
                                    }
                                    continue;
                                }
                                Record6<ULong, ULong, ULong, String, String, Byte> baseRecord = null;
                                List<Record6<ULong, ULong, ULong, String, String, Byte>> otherRecords = new ArrayList<>();

                                for (var record : entry.getValue()) {
                                    if (record.get(SECURITY_PROFILE.ID).equals(profileId)) {
                                        baseRecord = record;
                                    } else {
                                        otherRecords.add(record);
                                    }
                                }

                                if (otherRecords.isEmpty() && baseRecord != null) {
                                    records.add(baseRecord);
                                } else if (!otherRecords.isEmpty()) {
                                    records.sort(Comparator
                                            .comparingInt((Record6<ULong, ULong, ULong, String, String, Byte> e) -> pref
                                                    .getOrDefault(e.get(SECURITY_PROFILE.CLIENT_ID),
                                                            -1))
                                            .reversed());
                                    records.add(records.getLast());
                                }
                            }

                            return records;
                        }),

                (profiles, roles) -> Flux.from(
                                this.dslContext.select(
                                                SECURITY_V2_ROLE.ID,
                                                SECURITY_V2_ROLE.NAME,
                                                SECURITY_APP.APP_CODE)
                                        .from(SECURITY_V2_ROLE_ROLE)
                                        .leftJoin(SECURITY_V2_ROLE)
                                        .on(SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID
                                                .eq(SECURITY_V2_ROLE.ID))
                                        .leftJoin(SECURITY_APP)
                                        .on(SECURITY_V2_ROLE.APP_ID.eq(SECURITY_APP.ID))
                                        .where(SECURITY_V2_ROLE_ROLE.ROLE_ID
                                                .in(roles.stream().map(e -> e.getValue(SECURITY_V2_ROLE.ID))
                                                        .collect(Collectors.toSet()))))
                        .collectList(),

                (profiles, roles, subRoles) ->

                        Flux.from(this.dslContext
                                        .select(SecurityPermission.SECURITY_PERMISSION.NAME,
                                                SECURITY_APP.APP_CODE)
                                        .from(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION)
                                        .leftJoin(SecurityPermission.SECURITY_PERMISSION)
                                        .on(SecurityPermission.SECURITY_PERMISSION.ID.eq(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.PERMISSION_ID))
                                        .leftJoin(SECURITY_APP)
                                        .on(SECURITY_APP.ID.eq(SecurityPermission.SECURITY_PERMISSION.APP_ID))
                                        .where(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.ROLE_ID.in(Stream.concat(
                                                roles.stream().map(e -> e.getValue(SECURITY_V2_ROLE.ID)),
                                                subRoles.stream().map(e -> e.getValue(SECURITY_V2_ROLE.ID))).toList())))
                                .collectList(),

                (profiles, roles, subRoles, permissions) -> Mono.just(Stream.of(
                                roles.stream()
                                        .map(e -> AuthoritiesNameUtil.makeRoleName(
                                                e.getValue(SECURITY_APP.APP_CODE),
                                                e.getValue(SECURITY_V2_ROLE.NAME))),

                                subRoles.stream()
                                        .map(e -> AuthoritiesNameUtil.makeRoleName(
                                                e.getValue(SECURITY_APP.APP_CODE),
                                                e.getValue(SECURITY_V2_ROLE.NAME))),

                                permissions.stream()
                                        .map(e -> AuthoritiesNameUtil.makePermissionName(
                                                e.getValue(SECURITY_APP.APP_CODE),
                                                e.getValue(SecurityPermission.SECURITY_PERMISSION.NAME))),

                                profiles.stream()
                                        .map(e -> AuthoritiesNameUtil.makeProfileName(
                                                e.getValue(SECURITY_APP.APP_CODE),
                                                e.getValue(SECURITY_PROFILE.NAME))))
                        .filter(Objects::nonNull)
                        .flatMap(e -> e)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())

        ).contextWrite(
                Context.of(LogUtil.METHOD_NAME,
                        "ProfileDAO.getProfileAuthorities"));
    }

    public Mono<List<RoleV2>> getRolesForAssignmentInApp(ULong appId, ClientHierarchy hierarchy) {

        SelectConditionStep<Record1<ULong>> restrictions = this.dslContext.selectDistinct(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                .from(SECURITY_PROFILE_CLIENT_RESTRICTION)
                .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID))
                .where(DSL.and(SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID.eq(hierarchy.getClientId()),
                        SECURITY_PROFILE.APP_ID.eq(appId)));


        return FlatMapUtil.flatMapMono(
                () -> Flux.from(restrictions).map(Record1::value1).collectList(),

                restrictedProfiles -> {
                    if (!restrictedProfiles.isEmpty()) return Mono.just(restrictedProfiles);

                    return Flux.from(this.dslContext.selectDistinct(SECURITY_PROFILE.ID).from(SECURITY_PROFILE)
                            .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                            .where(DSL.and(SECURITY_PROFILE.APP_ID.eq(appId),
                                    DSL.or(
                                            SECURITY_PROFILE.CLIENT_ID.in(hierarchy.getClientIds()),
                                            SECURITY_PROFILE.CLIENT_ID.eq(SECURITY_APP.CLIENT_ID)
                                    )))).map(Record1::value1).collectList();
                },

                (rp, profileIds) -> Flux.from(
                                this.dslContext.select(SECURITY_V2_ROLE.fields())
                                        .from(SECURITY_PROFILE_ROLE)
                                        .leftJoin(SECURITY_V2_ROLE).on(SECURITY_V2_ROLE.ID.eq(SECURITY_PROFILE_ROLE.ROLE_ID))
                                        .where(SECURITY_PROFILE_ROLE.PROFILE_ID.in(profileIds)))
                        .map(rec -> rec.into(RoleV2.class)).collectList()
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.getRolesForAssignmentInApp"));
    }


    public Mono<Boolean> checkIfUserHasAnyProfile(ULong userId, String appCode) {

        return FlatMapUtil.flatMapMono(
                () -> Mono.from(this.dslContext.selectCount().from(SECURITY_PROFILE_USER)
                        .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                        .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                        .where(DSL.and(
                                SECURITY_PROFILE_USER.USER_ID.eq(userId),
                                SECURITY_APP.APP_CODE.eq((appCode))
                        ))).map(Record1::value1),

                profileCount -> {
                    if (profileCount > 0) return Mono.just(true);

                    return Mono.from(this.dslContext.selectCount().from(SECURITY_PROFILE)
                                    .leftJoin(SECURITY_APP).on(SECURITY_APP.ID.eq(SECURITY_PROFILE.APP_ID))
                                    .where(SECURITY_APP.APP_CODE.eq(appCode).and(SECURITY_PROFILE.DEFAULT_PROFILE.eq(ByteUtil.ONE))))
                            .map(Record1::value1).map(count -> count > 0);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDAO.checkIfUserHasAnyProfile"));
    }

    public Flux<ULong> getAssignedProfileIds(ULong userId, ULong appId) {
        return Flux.from(this.dslContext.select(SECURITY_PROFILE_USER.PROFILE_ID)
                .from(SECURITY_PROFILE_USER)
                .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                .where(DSL.and(
                        SECURITY_PROFILE_USER.USER_ID.eq(userId),
                        SECURITY_PROFILE.APP_ID.eq(appId)
                ))).map(Record1::value1);
    }

    public Flux<ULong> getUsersForProfiles(ULong appId, List<ULong> profileIds) {
        return Flux.from(this.dslContext.select(SECURITY_PROFILE_USER.USER_ID)
                .from(SECURITY_PROFILE_USER)
                .leftJoin(SECURITY_PROFILE).on(SECURITY_PROFILE.ID.eq(SECURITY_PROFILE_USER.PROFILE_ID))
                .where(DSL.and(
                        SECURITY_PROFILE_USER.PROFILE_ID.in(profileIds),
                        SECURITY_PROFILE.APP_ID.eq(appId)
                ))).map(Record1::value1).distinct();
    }

    public Mono<ULong> getUserAppHavingProfile(ULong userId) {
        return Mono.from(this.dslContext
                        .select(SECURITY_PROFILE.APP_ID)
                        .from(SECURITY_PROFILE)
                        .leftJoin(SECURITY_PROFILE_USER)
                        .on(SECURITY_PROFILE_USER.PROFILE_ID.eq(SECURITY_PROFILE.ID))
                        .where(SECURITY_PROFILE_USER.USER_ID.eq(userId))
                        .limit(1))
                .map(Record1::value1);
    }
}
