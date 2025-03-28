package com.fincity.security.dao;

import static com.fincity.saas.commons.util.StringUtil.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record6;
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
import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityAppAccess;
import com.fincity.security.jooq.tables.SecurityClientHierarchy;
import com.fincity.security.jooq.tables.SecurityPermission;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.SecurityProfileClientRestriction;
import com.fincity.security.jooq.tables.SecurityProfileRole;
import com.fincity.security.jooq.tables.SecurityProfileUser;
import com.fincity.security.jooq.tables.SecurityV2Role;
import com.fincity.security.jooq.tables.SecurityV2RolePermission;
import com.fincity.security.jooq.tables.SecurityV2RoleRole;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;
import com.fincity.security.jooq.tables.records.SecurityProfileRoleRecord;
import com.fincity.security.util.AuthoritiesNameUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

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

    public Mono<Profile> readRootProfile(ULong id, ClientHierarchy hierarchy, boolean includeClientId) {

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(this.dslContext.selectFrom(SecurityProfile.SECURITY_PROFILE)
                        .where(DSL.or(
                                SecurityProfile.SECURITY_PROFILE.ID.eq(id),
                                SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID.eq(id)
                                        .and(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
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

                    for (Profile profile : profiles.stream()
                            .sorted(Comparator.comparingInt((Profile e) -> clientPref
                                    .getOrDefault(e.getClientId(), -1))
                                    .reversed())
                            .toList()) {

                        if (profile.getId().equals(base.getId()))
                            continue;

                        base.setId(profile.getId());
                        base.setClientId(profile.getClientId());

                        if (!safeIsBlank(profile.getName()))
                            base.setName(profile.getName());

                        if (!safeIsBlank(profile.getDescription()))
                            base.setDescription(profile.getDescription());

                        DifferenceApplicator.apply(base.getArrangement(),
                                profile.getArrangement());
                    }
                    return Mono.just(base);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.readRootProfile"));
    }

    public Mono<Profile> createUpdateProfile(Profile profile, ClientHierarchy hierarchy) {

        if (profile.getId() == null || !hierarchy.getClientId().equals(profile.getClientId()))
            return create(profile, hierarchy);

        return update(profile, hierarchy);
    }

    @SuppressWarnings("unchecked")
    public Mono<Profile> create(Profile profile, ClientHierarchy hierarchy) {

        profile.setId(null);
        Map<String, Object> arrangements = profile.getArrangement();

        if (profile.getRootProfileId() == null) {
            return FlatMapUtil.flatMapMono(
                    () -> super.create(profile),

                    created -> this
                            .createRoleRelations(created.getId(),
                                    this.getRoleIdsFromArrangemnts(arrangements)
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

                (rootProfile, diff, created) -> {

                    Set<ULong> roleIds = this.getRoleIdsFromArrangemnts(arrangements)
                            .collect(Collectors.toSet());
                    Set<ULong> rootsRoleIds = this
                            .getRoleIdsFromArrangemnts(rootProfile.getArrangement())
                            .collect(Collectors.toSet());

                    Set<ULong> newRoles = roleIds.stream().filter(r -> !rootsRoleIds.contains(r))
                            .collect(Collectors.toSet());
                    Set<ULong> removedRoles = rootsRoleIds.stream()
                            .filter(r -> !roleIds.contains(r))
                            .collect(Collectors.toSet());

                    return this.createRoleRelations(created.getId(), newRoles, removedRoles)
                            .<Profile>flatMap(e -> this.read(created.getId(), hierarchy));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.create with rootProfileId"));
    }

    private Mono<Integer> createRoleRelations(ULong profileId, Set<ULong> newRoles, Set<ULong> removedRoles) {

        return FlatMapUtil.flatMapMono(
                () -> Mono.from(this.dslContext.delete(SecurityProfileRole.SECURITY_PROFILE_ROLE)
                        .where(SecurityProfileRole.SECURITY_PROFILE_ROLE.PROFILE_ID
                                .eq(profileId))),

                d -> {

                    List<SecurityProfileRoleRecord> records = new ArrayList<>();

                    newRoles.forEach(roleId -> {
                        SecurityProfileRoleRecord record = new SecurityProfileRoleRecord();
                        record.setProfileId(profileId);
                        record.setRoleId(roleId);
                        records.add(record);
                    });

                    removedRoles.forEach(roleId -> {
                        SecurityProfileRoleRecord record = new SecurityProfileRoleRecord();
                        record.setProfileId(profileId);
                        record.setRoleId(roleId);
                        record.setExclude(ByteUtil.ZERO);
                        records.add(record);
                    });

                    return Mono.from(this.dslContext
                            .insertInto(SecurityProfileRole.SECURITY_PROFILE_ROLE)
                            .values(records));
                }

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.createRoleRelations"));
    }

    @SuppressWarnings({ "unchecked" })
    private Stream<ULong> getRoleIdsFromArrangemnts(Map<String, Object> arrangements) {

        return arrangements.values().stream().flatMap(e -> {

            if (e instanceof Map m) {
                Object roleId = m.get("roleId");
                ULong rId = null;
                if (roleId != null && !Boolean.FALSE.equals(m.get("assignable"))) {
                    rId = ULong.valueOf(roleId.toString());
                }

                Object subArrangments = arrangements.get("subArrangements");
                if (subArrangments instanceof Map m1) {

                    Stream<ULong> stream = this.getRoleIdsFromArrangemnts((Map<String, Object>) m1);
                    if (rId == null)
                        return stream;
                    else
                        return Stream.concat(Stream.of(rId), stream);
                }
            }

            return Stream.empty();
        });
    }

    @SuppressWarnings("unchecked")
    private Mono<Profile> update(Profile profile, ClientHierarchy hierarchy) {

        Map<String, Object> arrangements = profile.getArrangement();

        if (profile.getRootProfileId() == null) {
            return FlatMapUtil.flatMapMono(

                    () -> Mono.from(this.dslContext.update(SecurityProfile.SECURITY_PROFILE)
                            .set(SecurityProfile.SECURITY_PROFILE.NAME, profile.getName())
                            .set(SecurityProfile.SECURITY_PROFILE.DESCRIPTION,
                                    profile.getDescription())
                            .set(SecurityProfile.SECURITY_PROFILE.ARRANGEMENT,
                                    profile.getArrangement())
                            .where(SecurityProfile.SECURITY_PROFILE.ID
                                    .eq(profile.getId()))),

                    updated -> this
                            .createRoleRelations(profile.getId(),
                                    this.getRoleIdsFromArrangemnts(arrangements)
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

                (rootProfile, diff, updated) -> {

                    Set<ULong> roleIds = this.getRoleIdsFromArrangemnts(arrangements)
                            .collect(Collectors.toSet());
                    Set<ULong> rootsRoleIds = this
                            .getRoleIdsFromArrangemnts(rootProfile.getArrangement())
                            .collect(Collectors.toSet());

                    Set<ULong> newRoles = roleIds.stream().filter(r -> !rootsRoleIds.contains(r))
                            .collect(Collectors.toSet());
                    Set<ULong> removedRoles = rootsRoleIds.stream()
                            .filter(r -> !roleIds.contains(r))
                            .collect(Collectors.toSet());

                    return this.createRoleRelations(updated.getId(), newRoles, removedRoles)
                            .<Profile>flatMap(e -> this.read(updated.getId(), hierarchy));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.update with rootProfileId"));
    }

    public Mono<Integer> delete(ULong profileId, ClientHierarchy hierarchy) {
        return FlatMapUtil.flatMapMono(
                () -> this.read(profileId, hierarchy),

                profile -> Mono.from(
                        this.dslContext.selectCount().from(SecurityProfile.SECURITY_PROFILE)
                                .where(SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID
                                        .eq(profile.getId())))
                        .map(Record1::value1)
                        .map(count -> count == 0)
                        .filter(BooleanUtil::safeValueOf),

                (profile, notUsed) -> super.delete(profile.getId())

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.delete"))
                .defaultIfEmpty(0);
    }

    public Mono<Boolean> hasAccessToRoles(ULong appId, ClientHierarchy hierarchy, Set<ULong> roleIds) {

        Mono<List<ULong>> profileIds = Flux.from(this.dslContext.select(SecurityProfile.SECURITY_PROFILE.ID)
                .where(DSL.and(SecurityProfile.SECURITY_PROFILE.APP_ID.eq(appId),
                        SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                .in(hierarchy.getClientIds()))))
                .map(Record1::value1)
                .collectList();

        Mono<List<ULong>> restrictedProfiles = Flux.from(this.dslContext
                .select(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                .where(DSL.and(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.APP_ID
                        .eq(appId),
                        SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
                                .eq(hierarchy.getClientId()))))
                .map(Record1::value1)
                .collectList();

        return FlatMapUtil.flatMapMono(

                () -> restrictedProfiles,

                restrictedProfileIds -> restrictedProfileIds.isEmpty() ? profileIds
                        : Mono.just(restrictedProfileIds),

                (restrictedProfileIds, ids) -> Flux
                        .from(this.dslContext.select(
                                SecurityProfileRole.SECURITY_PROFILE_ROLE.ROLE_ID)
                                .from(SecurityProfileRole.SECURITY_PROFILE_ROLE)
                                .where(DSL.and(SecurityProfileRole.SECURITY_PROFILE_ROLE.PROFILE_ID
                                        .in(ids),
                                        SecurityProfileRole.SECURITY_PROFILE_ROLE.ROLE_ID
                                                .notIn(roleIds))))
                        .map(Record1::value1)
                        .collectList(),

                (restrictedProfileIds, ids, remainingRoleIds) -> {
                    if (remainingRoleIds.isEmpty())
                        return Mono.just(true);

                    return Mono.from(this.dslContext.selectCount()
                            .from(SecurityV2Role.SECURITY_V2_ROLE)
                            .where(DSL.and(
                                    SecurityV2Role.SECURITY_V2_ROLE.ID
                                            .in(remainingRoleIds),
                                    DSL.or(
                                            SecurityV2Role.SECURITY_V2_ROLE.APP_ID
                                                    .eq(appId),
                                            SecurityV2Role.SECURITY_V2_ROLE.APP_ID
                                                    .isNull()),
                                    SecurityV2Role.SECURITY_V2_ROLE.CLIENT_ID
                                            .eq(hierarchy.getClientId()))))
                            .map(Record1::value1)
                            .map(count -> count == 0);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.hasAccessToRoles"));
    }

    public Mono<Boolean> hasAccessToRoles(ULong appId, ClientHierarchy hierarchy, Profile profile) {
        return this.hasAccessToRoles(appId, hierarchy,
                this.getRoleIdsFromArrangemnts(profile.getArrangement()).collect(Collectors.toSet()));
    }

    public Mono<Page<Profile>> readAll(ULong appId, ClientHierarchy hierarchy, Pageable pageable) {

        Mono<List<ULong>> restrictedProfiles = Flux.from(this.dslContext
                .select(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                .where(DSL.and(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.APP_ID
                        .eq(appId),
                        SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
                                .eq(hierarchy.getClientId()))))
                .map(Record1::value1)
                .collectList();

        Mono<List<ULong>> profiles = Flux
                .from(this.dslContext
                        .select(SecurityProfile.SECURITY_PROFILE.ID,
                                SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID)
                        .where(DSL.and(SecurityProfile.SECURITY_PROFILE.APP_ID.eq(appId),
                                SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                        .in(hierarchy.getClientIds()))))
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

        return Mono.from(this.dslContext.selectCount().from(SecurityProfile.SECURITY_PROFILE)
                .leftJoin(SecurityAppAccess.SECURITY_APP_ACCESS).on(
                        SecurityProfile.SECURITY_PROFILE.APP_ID
                                .eq(SecurityAppAccess.SECURITY_APP_ACCESS.APP_ID))
                .where(SecurityProfile.SECURITY_PROFILE.ID.eq(profileId)
                        .and(SecurityAppAccess.SECURITY_APP_ACCESS.CLIENT_ID.eq(clientId))))
                .map(Record1::value1)
                .map(count -> count > 0);
    }

    public Mono<Boolean> restrictClient(ULong profileId, ULong clientId) {
        return Mono
                .from(this.dslContext.insertInto(
                        SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .set(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID,
                                profileId)
                        .set(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID,
                                clientId))
                .map(e -> e > 0);
    }

    public Mono<Boolean> hasAccessToProfiles(ULong clientId, Set<ULong> profileIds) {

        return FlatMapUtil.flatMapMono(

                () -> Flux.from(
                        this.dslContext.select(SecurityApp.SECURITY_APP.ID)
                                .from(SecurityApp.SECURITY_APP)
                                .where(SecurityApp.SECURITY_APP.CLIENT_ID.eq(clientId))
                                .union(this.dslContext.select(
                                        SecurityAppAccess.SECURITY_APP_ACCESS.APP_ID)
                                        .from(SecurityAppAccess.SECURITY_APP_ACCESS)
                                        .where(SecurityAppAccess.SECURITY_APP_ACCESS.CLIENT_ID
                                                .eq(clientId))))
                        .map(Record1::value1)
                        .collectList().map(HashSet::new),

                appIds -> Flux.from(this.dslContext
                        .select(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.APP_ID,
                                SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.PROFILE_ID)
                        .from(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION)
                        .where(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION.CLIENT_ID
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
                            .from(SecurityProfile.SECURITY_PROFILE)
                            .where(SecurityProfile.SECURITY_PROFILE.ID
                                    .in(finalTuple.getT2())
                                    .and(SecurityProfile.SECURITY_PROFILE.APP_ID
                                            .notIn(finalTuple.getT1()))))
                            .map(Record1::value1)
                            .map(count -> count == 0);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileDao.readAll"));
    }

    public Mono<Set<ULong>> getProfileIds(String appCode, ULong userId) {
        return Flux.from(this.dslContext.select(SecurityProfileUser.SECURITY_PROFILE_USER.ID)
                .from(SecurityProfileUser.SECURITY_PROFILE_USER)
                .leftJoin(SecurityProfile.SECURITY_PROFILE)
                .on(SecurityProfileUser.SECURITY_PROFILE_USER.PROFILE_ID.eq(SecurityProfile.SECURITY_PROFILE.ID))
                .leftJoin(SecurityApp.SECURITY_APP)
                .on(SecurityProfile.SECURITY_PROFILE.APP_ID.eq(SecurityApp.SECURITY_APP.ID))
                .where(SecurityApp.SECURITY_APP.APP_CODE.eq(appCode))
                .and(SecurityProfileUser.SECURITY_PROFILE_USER.USER_ID.eq(userId))).map(Record1::value1)
                .collect(Collectors.toSet());
    }

    public Mono<Boolean> isBeingUsedByManagingClients(ULong clientId, ULong profileId, ULong rootProfileId) {

        ULong pId = rootProfileId == null ? profileId : rootProfileId;

        return Mono.from(this.dslContext.selectCount().from(SecurityProfile.SECURITY_PROFILE)
                .leftJoin(SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY)
                .on(SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                        .eq(SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY.CLIENT_ID))
                .where(
                        DSL.and(
                                SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID.eq(pId),
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

        return FlatMapUtil.flatMapMono(() -> Flux.from(this.dslContext.select(SecurityProfile.SECURITY_PROFILE.ID,
                SecurityProfile.SECURITY_PROFILE.NAME, SecurityApp.SECURITY_APP.APP_CODE)
                .from(SecurityProfile.SECURITY_PROFILE)
                .leftJoin(SecurityApp.SECURITY_APP)
                .on(SecurityApp.SECURITY_APP.ID.eq(SecurityProfile.SECURITY_PROFILE.APP_ID))
                .where(
                        DSL.or(
                                SecurityProfile.SECURITY_PROFILE.ID.eq(profileId),
                                DSL.and(
                                        SecurityProfile.SECURITY_PROFILE.ROOT_PROFILE_ID.eq(profileId),
                                        SecurityProfile.SECURITY_PROFILE.CLIENT_ID
                                                .in(clientHierarchy.getClientIds())))

                ))
                .collectList(),

                profiles -> Flux.from(this.dslContext.select(
                        SecurityV2Role.SECURITY_V2_ROLE.ID,
                        SecurityProfile.SECURITY_PROFILE.CLIENT_ID,
                        SecurityProfile.SECURITY_PROFILE.ID,
                        SecurityV2Role.SECURITY_V2_ROLE.NAME,
                        SecurityApp.SECURITY_APP.APP_CODE,
                        SecurityProfileRole.SECURITY_PROFILE_ROLE.EXCLUDE)
                        .from(SecurityProfileRole.SECURITY_PROFILE_ROLE)
                        .leftJoin(SecurityV2Role.SECURITY_V2_ROLE)
                        .on(SecurityProfileRole.SECURITY_PROFILE_ROLE.ROLE_ID.eq(SecurityV2Role.SECURITY_V2_ROLE.ID))
                        .leftJoin(SecurityApp.SECURITY_APP)
                        .on(SecurityV2Role.SECURITY_V2_ROLE.APP_ID.eq(SecurityApp.SECURITY_APP.ID))
                        .leftJoin(SecurityProfile.SECURITY_PROFILE)
                        .on(SecurityProfile.SECURITY_PROFILE.ID
                                .eq(SecurityProfileRole.SECURITY_PROFILE_ROLE.PROFILE_ID))
                        .where(SecurityProfileRole.SECURITY_PROFILE_ROLE.PROFILE_ID
                                .in(profiles.stream().map(Record3::value1).collect(Collectors.toSet()))))

                        .collect(Collectors.groupingBy(e -> e.get(SecurityV2Role.SECURITY_V2_ROLE.ID)))
                        .map(map -> {

                            List<Record6<ULong, ULong, ULong, String, String, Byte>> records = new ArrayList<>();

                            Map<ULong, Integer> pref = clientHierarchy.getClientOrder();

                            for (var entry : map.entrySet()) {
                                if (entry.getValue().size() == 1) {
                                    Byte bValue = entry.getValue().get(0)
                                            .get(SecurityProfileRole.SECURITY_PROFILE_ROLE.EXCLUDE);
                                    if (bValue == null || bValue == 1) {
                                        records.add(entry.getValue().get(0));
                                    }
                                    continue;
                                }
                                Record6<ULong, ULong, ULong, String, String, Byte> baseRecord = null;
                                List<Record6<ULong, ULong, ULong, String, String, Byte>> otherRecords = new ArrayList<>();

                                for (var record : entry.getValue()) {
                                    if (record.get(SecurityProfile.SECURITY_PROFILE.ID).equals(profileId)) {
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
                                                    .getOrDefault(e.get(SecurityProfile.SECURITY_PROFILE.CLIENT_ID),
                                                            -1))
                                            .reversed());
                                    records.add(records.getLast());
                                }
                            }

                            return records;
                        }),

                (profiles, roles) -> Flux.from(
                        this.dslContext.select(
                                SecurityV2Role.SECURITY_V2_ROLE.ID,
                                SecurityV2Role.SECURITY_V2_ROLE.NAME,
                                SecurityApp.SECURITY_APP.APP_CODE)
                                .from(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE)
                                .leftJoin(SecurityV2Role.SECURITY_V2_ROLE)
                                .on(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID
                                        .eq(SecurityV2Role.SECURITY_V2_ROLE.ID))
                                .leftJoin(SecurityApp.SECURITY_APP)
                                .on(SecurityV2Role.SECURITY_V2_ROLE.APP_ID.eq(SecurityApp.SECURITY_APP.ID))
                                .where(SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE.SUB_ROLE_ID
                                        .in(roles.stream().map(e -> e.getValue(SecurityV2Role.SECURITY_V2_ROLE.ID))
                                                .collect(Collectors.toSet()))))
                        .collectList(),

                (profiles, roles, subRoles) ->

                Flux.from(this.dslContext
                        .select(SecurityPermission.SECURITY_PERMISSION.NAME,
                                SecurityApp.SECURITY_APP.APP_CODE)
                        .from(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION)
                        .leftJoin(SecurityApp.SECURITY_APP)
                        .on(SecurityApp.SECURITY_APP.ID.eq(SecurityPermission.SECURITY_PERMISSION.APP_ID))
                        .where(SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION.ROLE_ID.in(Stream.concat(
                                roles.stream().map(e -> e.getValue(SecurityV2Role.SECURITY_V2_ROLE.ID)),
                                subRoles.stream().map(e -> e.getValue(SecurityV2Role.SECURITY_V2_ROLE.ID))).toList())))
                        .collectList(),

                (profiles, roles, subRoles, permissions) -> Mono.just(Stream.of(
                        roles.stream()
                                .map(e -> AuthoritiesNameUtil.makeRoleName(
                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                        e.getValue(SecurityV2Role.SECURITY_V2_ROLE.NAME))),

                        subRoles.stream()
                                .map(e -> AuthoritiesNameUtil.makeRoleName(
                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                        e.getValue(SecurityV2Role.SECURITY_V2_ROLE.NAME))),

                        permissions.stream()
                                .map(e -> AuthoritiesNameUtil.makePermissionName(
                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                        e.getValue(SecurityPermission.SECURITY_PERMISSION.NAME))),

                        profiles.stream()
                                .map(e -> AuthoritiesNameUtil.makeProfileName(
                                        e.getValue(SecurityApp.SECURITY_APP.APP_CODE),
                                        e.getValue(SecurityProfile.SECURITY_PROFILE.NAME))))
                        .flatMap(e -> e)
                        .distinct()
                        .toList())

        );
    }
}