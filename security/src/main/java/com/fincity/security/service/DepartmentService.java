package com.fincity.security.service;

import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.service.CacheService;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.DepartmentDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.jooq.tables.records.SecurityDepartmentRecord;

import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class DepartmentService
        extends AbstractJOOQUpdatableDataService<SecurityDepartmentRecord, ULong, Department, DepartmentDAO> {

    private static final String DEPARTMENT = "Department";
    private static final String FETCH_PARENT_DEPARTMENT = "fetchParentDepartment";

    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String PARENT_DEPARTMENT_ID = "parentDepartmentId";

    private final SecurityMessageResourceService securityMessageResourceService;
    private final ClientService clientService;
    private final CacheService cacheService;

    private static final String CACHE_NAME_DEPARTMENT = "department";

    public DepartmentService(SecurityMessageResourceService securityMessageResourceService,
                             ClientService clientService,
                             CacheService cacheService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
        this.cacheService = cacheService;
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Department> create(Department entity) {
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
                        (ca, managed) -> this.checkSameClient(entity.getClientId(), entity.getParentDepartmentId()),

                        (ca, managed, sameClient) -> super.create(entity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DepartmentService.create"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), DEPARTMENT, entity)));
    }

    private Mono<Boolean> checkSameClient(ULong clientId, ULong departmentId) {
        if (departmentId == null)
            return Mono.just(true);

        return this.dao.checkSameClient(clientId, departmentId).filter(BooleanUtil::safeValueOf);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Department> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Page<Department>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Department> update(Department entity) {

        return FlatMapUtil.flatMapMono(
                        () -> this.checkSameClient(entity.getClientId(), entity.getParentDepartmentId()),

                        managed -> this.dao.canBeUpdated(entity.getId()).filter(BooleanUtil::safeValueOf),

                        (managed, canBeUpdated) -> super.update(entity),
                        (managed, canBeUpdated, updatedDepartment) ->
                                this.cacheService
                                        .evict(CACHE_NAME_DEPARTMENT, updatedDepartment.getId())
                                .thenReturn(updatedDepartment)
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DepartmentService.update"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), DEPARTMENT, entity)));
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Department> update(ULong key, Map<String, Object> fields) {
        return FlatMapUtil.flatMapMono(
                        () -> this.dao.canBeUpdated(key)
                                .filter(BooleanUtil::safeValueOf),

                        canBeUpdated -> super.update(key, fields),

                        (canBeUpdated, updatedDepartment) -> this.cacheService
                                .evict(CACHE_NAME_DEPARTMENT, updatedDepartment.getId())
                                .thenReturn(updatedDepartment)
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DepartmentService.update"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), DEPARTMENT, fields)));
    }

    @Override
    protected Mono<Department> updatableEntity(Department entity) {
        return this.read(entity.getId())
                .map(e -> {
                    e.setName(entity.getName());
                    e.setDescription(entity.getDescription());
                    return e;
                });
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return super.delete(id);
    }

    public Mono<Map<ULong, Tuple2<AppRegistrationDepartment, Department>>> createForRegistration(
            Client client,
            List<AppRegistrationDepartment> departments) {

        if (departments == null || departments.isEmpty())
            return Mono.just(Map.of());

        return this.dao.createForRegistration(client, departments);
    }

    public Mono<Department> readInternal(ULong departmentId) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_DEPARTMENT, () -> this.dao.readInternal(departmentId), departmentId);
    }

    public Mono<List<Department>> fillDetails(List<Department> departments, MultiValueMap<String, String> queryParams) {

        boolean fetchParentDepartment = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_PARENT_DEPARTMENT));

        Flux<Department> departmentFlux = Flux.fromIterable(departments);

        if (fetchParentDepartment)
            departmentFlux = departmentFlux.filter(department -> department.getParentDepartmentId() != null && department.getParentDepartmentId().intValue() != 0).flatMap(department -> this.readInternal(department.getParentDepartmentId()).map(department::setParentDepartment));

        return departmentFlux.collectList();
    }
}
