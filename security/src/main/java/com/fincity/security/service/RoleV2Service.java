package com.fincity.security.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.types.ULong;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;
import com.fincity.security.service.appregistration.IAppRegistrationHelperService;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class RoleV2Service
        extends AbstractSecurityUpdatableDataService<SecurityV2RoleRecord, ULong, RoleV2, RoleV2DAO>
        implements IAppRegistrationHelperService {

    private static final String ROLE = "Role";
    private static final String DESCRIPTION = "description";
    private static final String NAME = "name";
    private static final String SHORT_NAME = "shortName";

    private final SecurityMessageResourceService securityMessageResourceService;
    private final ClientService clientService;
    private final ClientHierarchyService clientHierarchyService;
    private final CacheService cacheService;

    public RoleV2Service(SecurityMessageResourceService securityMessageResourceService, ClientService clientService,
            ClientHierarchyService clientHierarchyService, CacheService cacheService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
        this.clientHierarchyService = clientHierarchyService;
        this.cacheService = cacheService;
    }

    @PreAuthorize("hasAuthority('Authorities.Role_CREATE')")
    @Override
    public Mono<RoleV2> create(RoleV2 entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (entity.getClientId() == null)
                        return Mono.just(entity.setClientId(ULong.valueOf(ca.getUser().getClientId())));

                    if (ca.isSystemClient())
                        return Mono.just(entity);

                    return this.clientService
                            .isUserClientManageClient(ca, entity.getClientId())
                            .filter(BooleanUtil::safeValueOf)
                            .map(x -> entity);
                },

                (ca, managed) -> super.create(entity)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.create"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService
                        .getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
                                StringFormatter.format(msg, ROLE))))));
    }

    @PreAuthorize("hasAuthority('Authorities.Role_READ')")
    @Override
    public Mono<RoleV2> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAuthority('Authorities.Role_READ')")
    @Override
    public Mono<Page<RoleV2>> readPageFilter(Pageable pageable, AbstractCondition cond) {
        return super.readPageFilter(pageable, cond).flatMap(this::enrich);
    }

    /**
     * A listed role carries only its own columns, which leaves a caller with an
     * `appId` it cannot name and no idea what the role actually grants. Two extra
     * queries per page fill in the app code and the sub-roles, so the list is
     * readable without a call per row.
     */
    private Mono<Page<RoleV2>> enrich(Page<RoleV2> page) {

        List<RoleV2> roles = page.getContent();
        if (roles.isEmpty())
            return Mono.just(page);

        return FlatMapUtil.flatMapMono(

                () -> this.dao.appCodesOf(roles.stream()
                        .map(RoleV2::getAppId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())),

                appCodes -> this.dao.fetchSubRoles(roles.stream().map(RoleV2::getId).toList()),

                (appCodes, subRoles) -> {
                    for (RoleV2 role : roles) {
                        if (role.getAppId() != null)
                            role.setAppName(appCodes.get(role.getAppId()));
                        role.setSubRoles(subRoles.getOrDefault(role.getId(), List.of()));
                    }
                    return Mono.just(page);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.enrich"));
    }

    @PreAuthorize("hasAuthority('Authorities.Role_UPDATE')")
    @Override
    public Mono<RoleV2> update(RoleV2 entity) {
        return this.dao.canBeUpdated(entity.getId())
                .filter(BooleanUtil::safeValueOf)
                .flatMap(x -> super.update(entity))
                .switchIfEmpty(Mono.defer(
                        () -> securityMessageResourceService.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
                                .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
                                        StringFormatter.format(msg, ROLE, entity.getId()))))));
    }

    @PreAuthorize("hasAuthority('Authorities.Role_UPDATE')")
    @Override
    public Mono<RoleV2> update(ULong id, Map<String, Object> fields) {
        return this.dao.canBeUpdated(id)
                .filter(BooleanUtil::safeValueOf)
                .flatMap(x -> super.update(id, fields))
                .switchIfEmpty(Mono.defer(
                        () -> securityMessageResourceService.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
                                .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
                                        StringFormatter.format(msg, ROLE, id))))));
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.ROLE;
    }

    @Override
    protected ULong resolveClientId(RoleV2 entity) {
        return entity.getClientId();
    }

    @Override
    protected String describeEntity(RoleV2 entity) {
        return entity == null ? null : entity.getName();
    }

    @Override
    protected Mono<RoleV2> updatableEntity(RoleV2 entity) {
        return this.read(entity.getId())
                .map(existing -> {
                    existing.setShortName(entity.getShortName());
                    existing.setDescription(entity.getDescription());
                    existing.setName(entity.getName());
                    return existing;
                });
    }

    @PreAuthorize("hasAuthority('Authorities.Role_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.read(id),

                (ca, existing) -> super.delete(id)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.create"))
                .onErrorResume(
                        ex -> ex instanceof DataAccessException || ex instanceof R2dbcDataIntegrityViolationException
                                ? this.securityMessageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg, ex),
                                        SecurityMessageResourceService.DELETE_ROLE_ERROR)
                                : Mono.error(ex));
    }

    @Override
    public Mono<RoleV2> readObject(ULong id,
            AppRegistrationObjectType type) {
        return super.read(id);
    }

    @Override
    public Mono<Boolean> hasAccessTo(ULong id, ULong clientId, AppRegistrationObjectType type) {
        return FlatMapUtil.flatMapMono(

                () -> super.read(id),

                role -> this.clientService.doesClientManageClient(role.getClientId(), clientId)
                        .flatMap(e -> BooleanUtil.safeValueOf(e) ? Mono.just(true)
                                : this.clientService.doesClientManageClient(clientId, role.getClientId()))

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.hasAccessTo"));
    }

    public Mono<Map<ULong, RoleV2>> getRolesForProfileService(Collection<ULong> roleIds) {
        return this.dao.getRoles(roleIds)
                .map(lst -> lst.stream().collect(Collectors.toMap(RoleV2::getId, Function.identity())));
    }

    public Mono<Map<String, List<String>>> getRoleAuthoritiesPerApp(ULong userId) {
        return this.dao.getRoleAuthoritiesPerApp(userId);
    }

    public Mono<List<RoleV2>> getRolesForAssignmentInApp(String appCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                (ca, hierarchy) -> this.dao.getRolesForAssignmentInApp(appCode, hierarchy))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.getRolesForAssignmentInApp"));
    }

    public Mono<List<RoleV2>> fetchSubRolesAlso(List<RoleV2> list) {
        return this.dao.fetchSubRoles(list.stream().map(RoleV2::getId).toList())
                .map(subRoleMap -> {

                    for (RoleV2 r : list) {
                        if (!subRoleMap.containsKey(r.getId()))
                            continue;
                        r.setSubRoles(subRoleMap.get(r.getId()));
                    }

                    return Stream.concat(list.stream(), subRoleMap.values().stream().flatMap(List::stream))
                            .collect(Collectors.toList());
                });
    }
    // ── the sub-role tree ─────────────────────────────────────────────────────
    //
    // A role's granted authorities are its own name plus everything its sub-roles
    // carry, so nesting one role under another is how a coarse role is composed
    // out of fine-grained ones. `security_v2_role_role` held that tree from day
    // one and no API ever wrote to it: every row came from a migration or the seed
    // SQL, which is why the App Builder could show roles and never compose them.

    @PreAuthorize("hasAuthority('Authorities.Role_READ')")
    public Mono<List<RoleV2>> getSubRoles(ULong id) {

        return FlatMapUtil.flatMapMono(

                () -> super.read(id),

                role -> this.dao.fetchSubRolesOf(id)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.getSubRoles"));
    }

    @PreAuthorize("hasAuthority('Authorities.Role_UPDATE') and hasAuthority('Authorities.Role_READ')")
    public Mono<Boolean> assignSubRole(ULong roleId, ULong subRoleId) {

        if (roleId.equals(subRoleId))
            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.SUB_ROLE_SELF);

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> super.read(roleId),

                (ca, role) -> super.read(subRoleId),

                (ca, role, subRole) -> this.canManage(ca, role).flatMap(x -> this.canManage(ca, subRole)),

                // The tree is data and nothing stops it looping, so the edit that
                // would close a loop is refused here rather than left to blow up
                // later inside the authority walk.
                (ca, role, subRole, allowed) -> this.dao.descendantsOf(subRoleId),

                (ca, role, subRole, allowed, below) -> below.contains(roleId)
                        ? this.securityMessageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.SUB_ROLE_CYCLE, subRole.getName(), role.getName())
                        : this.dao.hasSubRole(roleId, subRoleId)
                                .flatMap(has -> BooleanUtil.safeValueOf(has)
                                        ? Mono.just(Boolean.TRUE)
                                        : this.dao.addSubRole(roleId, subRoleId)),

                (ca, role, subRole, allowed, below, added) -> this.evictRoleAuthorities(roleId)
                        .thenReturn(added)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.assignSubRole"))
                .switchIfEmpty(Mono.defer(() -> this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE, ROLE)));
    }

    @PreAuthorize("hasAuthority('Authorities.Role_UPDATE') and hasAuthority('Authorities.Role_READ')")
    public Mono<Boolean> removeSubRole(ULong roleId, ULong subRoleId) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> super.read(roleId),

                (ca, role) -> this.canManage(ca, role),

                (ca, role, allowed) -> this.dao.removeSubRole(roleId, subRoleId),

                (ca, role, allowed, removed) -> this.evictRoleAuthorities(roleId)
                        .thenReturn(removed > 0)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.removeSubRole"))
                .switchIfEmpty(Mono.defer(() -> this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_UPDATE, ROLE)));
    }

    private Mono<Boolean> canManage(ContextAuthentication ca, RoleV2 role) {

        if (ca.isSystemClient())
            return Mono.just(true);

        return this.clientService.isUserClientManageClient(ca, role.getClientId())
                .flatMap(BooleanUtil::safeValueOfWithEmpty);
    }

    /**
     * Both caches that hold role-derived authorities. `userRoles` is keyed by user
     * and a sub-role edit can reach any user holding an ancestor of this role, so
     * that one goes wholesale; the per-profile caches are named individually, and
     * `evictAll` publishes to every instance rather than only this one.
     */
    private Mono<Boolean> evictRoleAuthorities(ULong roleId) {

        return this.dao.ancestorsOf(roleId)
                .flatMap(this.dao::profileIdsWithAnyRole)
                .flatMap(profileIds -> Flux.fromIterable(profileIds)
                        .flatMap(pid -> this.cacheService
                                .evictAll(ProfileService.CACHE_AUTHORITIES_BY_ID + "_" + pid))
                        .then(this.cacheService.evictAll(UserService.CACHE_NAME_USER_ROLE)));
    }
}
