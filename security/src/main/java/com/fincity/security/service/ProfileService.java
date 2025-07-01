package com.fincity.security.service;

import java.util.*;
import java.util.stream.Collectors;

import static com.fincity.saas.commons.util.CommonsUtil.*;

import com.fincity.saas.commons.util.StringUtil;
import com.google.common.base.Functions;
import org.apache.commons.lang3.NotImplementedException;
import org.jooq.exception.DataAccessException;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ProfileDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;
import com.fincity.security.service.appregistration.IAppRegistrationHelperService;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ProfileService
        extends AbstractSecurityUpdatableDataService<SecurityProfileRecord, ULong, Profile, ProfileDAO>
        implements IAppRegistrationHelperService {

    private static final String PROFILE = "Profile";

    private static final String DESCRIPTION = "description";
    private static final String NAME = "name";

    private static final String CACHE_NAME_ID = "profileId";

    private final SecurityMessageResourceService securityMessageResourceService;
    private final ClientService clientService;
    private final ClientHierarchyService clientHierarchyService;
    private final AppService appService;
    private final CacheService cacheService;
    private final RoleV2Service roleService;

    public ProfileService(SecurityMessageResourceService securityMessageResourceService,
                          ClientService clientService,
                          ClientHierarchyService clientHierarchyService, CacheService cacheService,
                          AppService appService, RoleV2Service roleService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
        this.clientHierarchyService = clientHierarchyService;
        this.cacheService = cacheService;
        this.appService = appService;
        this.roleService = roleService;
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_CREATE') and hasAuthority('Authorities.Profile_UPDATE')")
    @Override
    public Mono<Profile> create(Profile entity) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> {
                            if (entity.getAppId() == null)
                                return this.securityMessageResourceService.<Boolean>throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST,
                                                msg),
                                        SecurityMessageResourceService.PROFILE_NEEDS_APP);

                            // Checking whether the profile's client id has access to the app or not.
                            return this.appService.hasReadAccess(entity.getAppId(), entity.getClientId())
                                    .filter(BooleanUtil::safeValueOf);
                        },

                        (ca, hasAppAccess) -> {

                            if (ca.isSystemClient())
                                return Mono.just(true);

                            return this.appService
                                    .hasReadAccess(entity.getAppId(),
                                            ULong.valueOf(ca.getUser().getClientId()))
                                    .filter(BooleanUtil::safeValueOf);
                        },

                        (ca, hasAppAccess, clientAlsoHasAppAccess) -> {

                            if (entity.getClientId() == null)
                                return Mono.just(entity.setClientId(
                                        ULong.valueOf(ca.getUser().getClientId())));

                            if (ca.isSystemClient())
                                return Mono.just(entity);

                            return this.clientService
                                    .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
                                            entity.getClientId())
                                    .filter(BooleanUtil::safeValueOf)
                                    .map(x -> entity);
                        },

                        (ca, hasAppAccess, clientAlsoHasAppAccess, managed) -> clientHierarchyService
                                .getClientHierarchy(entity.getClientId()),

                        (ca, hasAppAccess, clientAlsoHasAppAccess, managed,
                         clientHierarchy) -> this.dao.hasAccessToRoles(entity.getAppId(),
                                clientHierarchy,
                                entity),

                        (ca, hasAppAccess, clientAlsoHasAppAccess, managed, clientHierarchy,
                         hasAccessToRoles) -> {
                            if (!hasAccessToRoles) {
                                return this.securityMessageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST,
                                                msg),
                                        SecurityMessageResourceService.FORBIDDEN_ROLE_ACCESS);
                            }
                            return this.cleanProfileArrangment(entity);
                        },

                        (ca, hasAppAccess, clientAlsoHasAppAccess, managed, clientHierarchy,
                         hasAccessToRoles, cleanedEntity) -> this.dao.createUpdateProfile(cleanedEntity, ULong.valueOf(ca.getUser().getId()), clientHierarchy)
                                .flatMap(e -> this.fillProfileArrangements(e, true))

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.create"))
                .flatMap(e -> {

                    String cacheName = CACHE_NAME_ID + "_"
                            + (e.getRootProfileId() == null ? e.getId()
                            : e.getRootProfileId());

                    if (e.getRootProfileId() == null)
                        return this.cacheService.evictAll(cacheName).map(x -> e);

                    return this.dao.isBeingUsedByManagingClients(e.getClientId(), e.getId(),
                                    e.getRootProfileId())
                            .flatMap(used -> {

                                if (used)
                                    return this.cacheService.evictAll(cacheName);

                                return this.cacheService
                                        .evict(cacheName, e.getClientId());
                            }).map(x -> e);
                })
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService
                        .getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
                                StringFormatter.format(msg, PROFILE))))));
    }

    private Mono<Profile> cleanProfileArrangment(Profile p) {

        if (p.getArrangement() == null) return Mono.just(p);

        LinkedList<Map<String, Object>> queue = new LinkedList<>();
        p.getArrangement().values().forEach(e -> {
            if (e instanceof Map m) queue.add(m);
        });
        int i = 0;

        while (i < queue.size()) {

            Object obj = queue.get(i).get("subArrangements");
            if (obj instanceof Map m)
                queue.addAll(m.values());

            i++;
        }

        Map<ULong, List<Map<String, Object>>> roleIndex = queue.stream().filter(m -> Objects.nonNull(m.get("roleId")))
                .collect(Collectors.groupingBy(m -> ULong.valueOf(m.get("roleId").toString())));

        if (roleIndex.isEmpty()) return Mono.just(p);

        return this.roleService.getRolesForProfileService(roleIndex.keySet())
                .map(map -> {
                            roleIndex.forEach((key, arrangements) -> {
                                RoleV2 role = map.get(key);

                                arrangements.forEach(arrangement -> {
                                    arrangement.remove("role");
                                    if (safeEquals(arrangement.get("name"), role.getName()))
                                        arrangement.remove("name");

                                    if (safeEquals(arrangement.get("shortName"), role.getShortName()))
                                        arrangement.remove("shortName");

                                    if (safeEquals(arrangement.get("description"), role.getDescription()))
                                        arrangement.remove("description");

                                });
                            });
                            return map;
                        }
                ).map(x -> p);
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ')")
    @Override
    public Mono<Profile> read(ULong id) {
        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientHierarchyService
                                .getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                        (ca, clientHierarchy) -> this.dao.read(id, clientHierarchy),

                        (ca, clientHierarchy, profile) -> this.appService
                                .hasReadAccess(profile.getAppId(),
                                        ULong.valueOf(ca.getUser().getClientId()))
                                .filter(BooleanUtil::safeValueOf)
                                .<Profile>map(x -> profile)
                                .flatMap(p -> this.fillProfileArrangements(p, true))

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.read"));
    }

    private Mono<Profile> fillProfileArrangements(Profile p, boolean fillArrangement) {

        if (p.getArrangement() == null) return Mono.just(p);

        LinkedList<Map<String, Object>> queue = new LinkedList<>();
        p.getArrangement().values().forEach(e -> {
            if (e instanceof Map m) queue.add(m);
        });
        int i = 0;

        while (i < queue.size()) {

            Object obj = queue.get(i).get("subArrangements");
            if (obj instanceof Map m)
                queue.addAll(m.values());
            queue.get(i).putIfAbsent("assignable", true);
            i++;
        }

        Map<ULong, Map<String, Object>> roleIndex = queue.stream().filter(m -> Objects.nonNull(m.get("roleId")))
                .collect(Collectors.toMap(m -> ULong.valueOf(m.get("roleId").toString()), Functions.identity()));

        if (roleIndex.isEmpty()) return Mono.just(p);

        return this.roleService.getRolesForProfileService(roleIndex.keySet())
                .map(map -> {
                            roleIndex.entrySet().forEach(entry -> {
                                Map<String, Object> arrangement = entry.getValue();
                                RoleV2 role = map.get(entry.getKey());

                                arrangement.put("role", map.get(entry.getKey()));

                                if (!fillArrangement) return;

                                arrangement.putIfAbsent("name", role.getName());
                                arrangement.putIfAbsent("shortName", role.getShortName());
                                arrangement.putIfAbsent("description", role.getDescription());
                            });
                            return map;
                        }
                ).map(x -> p);

    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ')")
    public Mono<Page<Profile>> readAll(ULong appId, Pageable pageable) {
        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientHierarchyService
                                .getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                        (ca, clientHierarchy) -> this.dao.readAll(appId, clientHierarchy, pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.readAll"));
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.PROFILE;
    }

    @Override
    protected Mono<Profile> updatableEntity(Profile entity) {
        return Mono.error(NotImplementedException::new);
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientHierarchyService
                                .getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                        (ca, hierarchy) -> this.dao.delete(id, hierarchy)

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.delete"))
                .onErrorResume(
                        ex -> ex instanceof DataAccessException
                                || ex instanceof R2dbcDataIntegrityViolationException
                                ? this.securityMessageResourceService
                                .throwMessage(
                                        msg -> new GenericException(
                                                HttpStatus.FORBIDDEN,
                                                msg,
                                                ex),
                                        SecurityMessageResourceService.DELETE_ROLE_ERROR)
                                : Mono.error(ex));
    }

    @PreAuthorize("hasAuthority('Authorities.Profile_READ') and hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> restrictClient(ULong profileId, ULong clientId) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientService
                                .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), clientId)
                                .filter(BooleanUtil::safeValueOf),

                        (ca, managed) -> this.dao.checkProfileAppAccess(profileId, clientId)
                                .filter(BooleanUtil::safeValueOf),

                        (ca, managed, hasAppAccess) -> this.dao.restrictClient(profileId, clientId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.restrictClient"));
    }

    public Mono<Boolean> hasAccessToRoles(ULong clientId, Set<ULong> roleIds) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientHierarchyService
                                .getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                        (ca, clientHierarchy) -> this.dao.hasAccessToRoles(clientId, clientHierarchy, roleIds))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.hasAccessToRoles"));
    }

    public Mono<Boolean> hasAccessToProfiles(ULong clientId, Set<ULong> profileIds) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientHierarchyService
                        .getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                (ca, clientHierarchy) -> this.dao.hasAccessToProfiles(clientId, profileIds)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.hasAccessToProfiles"));
    }

    public Mono<List<String>> getProfileAuthorities(Set<ULong> profileIds, ClientHierarchy clientHierarchy) {

        return Flux.fromIterable(profileIds)
                .flatMap(
                        pid -> cacheService.cacheValueOrGet(CACHE_NAME_ID + "_" + pid,
                                        () -> this.dao.getProfileAuthorities(pid,
                                                        clientHierarchy).defaultIfEmpty(List.of())
                                                .map(e -> {
                                                    System.out.println("List (" + pid + "): " + e);
                                                    return e;
                                                }),
                                        clientHierarchy.getClientId())
                                .flatMapMany(Flux::fromIterable))
                .filter(Objects::nonNull)
                .distinct()
                .collectList();
    }

    public Mono<List<String>> getProfileAuthorities(String appCode, ULong clientId, ULong userId) {

        return FlatMapUtil.flatMapMono(

                        () -> this.dao.getProfileIds(appCode, userId),

                        profileIds -> this.clientHierarchyService.getClientHierarchy(clientId),

                        this::getProfileAuthorities

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.getProfileAuthorities"));
    }

    @Override
    public Mono<Profile> readObject(ULong id,
                                    AppRegistrationObjectType type) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                        (ca, hierarchy) -> this.dao.read(id, hierarchy))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.readObject"));
    }

    @Override
    public Mono<Boolean> hasAccessTo(ULong id, ULong clientId, AppRegistrationObjectType type) {
        return FlatMapUtil.flatMapMono(

                        () -> this.clientHierarchyService.getClientHierarchy(clientId).flatMap(x -> this.dao.read(id, x)),

                        profile -> this.clientService.isBeingManagedBy(profile.getClientId(), clientId)
                                .flatMap(e -> BooleanUtil.safeValueOf(e) ? Mono.just(true)
                                        : this.clientService.isBeingManagedBy(clientId, profile.getClientId()))

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.hasAccessTo"));
    }

    public Mono<List<RoleV2>> getRolesForAssignmentInApp(String appCode) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.appService.getAppId(appCode),

                        (ca, appId) -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),


                        (ca, appId, hierarchy) -> this.appService.hasReadAccess(appId, hierarchy.getClientId()).filter(BooleanUtil::safeValueOf),

                        (ca, appId, hierarchy, hasAccess) -> this.dao.getRolesForAssignmentInApp(appId, hierarchy)
                ).contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.getRolesForAssignmentInApp"))
                .switchIfEmpty(Mono.defer(() -> this.securityMessageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg)
                        , SecurityMessageResourceService.FORBIDDEN_ROLE_ACCESS)));
    }

    public Mono<Boolean> checkIfUserHasAnyProfile(ULong userId, String appCode) {

        if (userId == null || StringUtil.safeIsBlank(appCode)) return Mono.just(false);

        return this.dao.checkIfUserHasAnyProfile(userId, appCode);
    }

    public Mono<List<Profile>> assignedProfiles(ULong userId, ULong appId) {
        return this.dao.getAssignedProfileIds(userId, appId)
                .distinct()
                .flatMap(this::read)
                .collectList();
    }

    public Mono<List<ULong>> getUsersForProfiles(ULong appId, List<ULong> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Mono.just(List.of());
        }

        return this.dao.getUsersForProfiles(appId, profileIds)
                .collectList()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProfileService.getUsersForProfiles"));
    }

    public Mono<ULong> getUserAppHavingProfile(ULong userId) {

        if (userId == null) return Mono.empty();

        return  this.dao.getUserAppHavingProfile(userId);

    }

}
