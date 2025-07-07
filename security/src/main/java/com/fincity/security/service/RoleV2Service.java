package com.fincity.security.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Functions;
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
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;
import com.fincity.security.service.appregistration.IAppRegistrationHelperService;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
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

    public RoleV2Service(SecurityMessageResourceService securityMessageResourceService, ClientService clientService,
                         ClientHierarchyService clientHierarchyService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
        this.clientHierarchyService = clientHierarchyService;
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
                                    .isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), entity.getClientId())
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
        return super.readPageFilter(pageable, cond);

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

                        role -> this.clientService.isBeingManagedBy(role.getClientId(), clientId)
                                .flatMap(e -> BooleanUtil.safeValueOf(e) ? Mono.just(true)
                                        : this.clientService.isBeingManagedBy(clientId, role.getClientId()))

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.hasAccessTo"));
    }


    public Mono<Map<ULong, RoleV2>> getRolesForProfileService(Collection<ULong> roleIds) {
        return this.dao.getRoles(roleIds).map(lst ->
                lst.stream().collect(Collectors.toMap(RoleV2::getId, Functions.identity())));
    }

    public Mono<Map<String, List<String>>> getRoleAuthoritiesPerApp(ULong userId) {
        return this.dao.getRoleAuthoritiesPerApp(userId);
    }

    public Mono<List<RoleV2>> getRolesForAssignmentInApp(String appCode) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.clientHierarchyService.getClientHierarchy(ULong.valueOf(ca.getUser().getClientId())),

                (ca, hierarchy) -> this.dao.getRolesForAssignmentInApp(appCode, hierarchy)
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "RoleV2Service.getRolesForAssignmentInApp"));
    }

    public Mono<List<RoleV2>> fetchSubRolesAlso(List<RoleV2> list) {
        return this.dao.fetchSubRoles(list.stream().map(RoleV2::getId).toList())
                .map(subRoleMap -> {

                    for (RoleV2 r : list) {
                        if (!subRoleMap.containsKey(r.getId())) continue;
                        r.setSubRoles(subRoleMap.get(r.getId()));
                    }

                    return Stream.concat(list.stream(), subRoleMap.values().stream().flatMap(List::stream)).collect(Collectors.toList());
                });
    }
}
