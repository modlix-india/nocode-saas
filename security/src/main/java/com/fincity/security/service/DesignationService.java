package com.fincity.security.service;

import java.util.List;
import java.util.Map;

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
import com.fincity.security.dao.DesignationDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.Designation;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationDesignation;
import com.fincity.security.jooq.tables.records.SecurityDesignationRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class DesignationService
        extends AbstractJOOQUpdatableDataService<SecurityDesignationRecord, ULong, Designation, DesignationDAO> {

    private static final String DESIGNATION = "Designation";

    private static final String NAME = "name";
    private static final String DESCRIPTION = "description";
    private static final String PARENT_DESIGNATION_ID = "parentDesignationId";
    private static final String NEXT_DESIGNATION_ID = "nextDesignationId";
    private static final String DEPARTMENT_ID = "departmentId";

    private final SecurityMessageResourceService securityMessageResourceService;
    private final ClientService clientService;

    public DesignationService(SecurityMessageResourceService securityMessageResourceService,
                              ClientService clientService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.clientService = clientService;
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Designation> create(Designation entity) {
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
                        (ca, managed) -> this.checkSameClient(entity.getClientId(), entity.getParentDesignationId(),
                                entity.getNextDesignationId(), entity.getDepartmentId()),

                        (ca, managed, sameClient) -> super.create(entity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DesignationService.create"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), DESIGNATION, entity)));
    }

    private Mono<Boolean> checkSameClient(ULong clientId, ULong parentDesignationId, ULong nextDesignationId,
                                          ULong departmentId) {
        if (departmentId == null)
            return Mono.just(true);

        return this.dao.checkSameClient(clientId, parentDesignationId, nextDesignationId, departmentId)
                .filter(BooleanUtil::safeValueOf);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Designation> read(ULong id) {
        return super.read(id);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Page<Designation>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Designation> update(Designation entity) {

        return FlatMapUtil.flatMapMono(
                        () -> this.checkSameClient(entity.getClientId(), entity.getParentDesignationId(),
                                entity.getNextDesignationId(), entity.getDepartmentId()),

                        managed -> this.dao.canBeUpdated(entity.getId()).filter(BooleanUtil::safeValueOf),

                        (managed, canBeUpdated) -> super.update(entity)

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DesignationService.update"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg), DESIGNATION, entity.getId())));
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Client_CREATE', 'Authorities.Client_UPDATE')")
    @Override
    public Mono<Designation> update(ULong key, Map<String, Object> fields) {
        return FlatMapUtil.flatMapMono(
                        () -> this.dao.canBeUpdated(key).filter(BooleanUtil::safeValueOf),

                        canBeUpdated -> super.update(key, fields)

                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DesignationService.update"))
                .switchIfEmpty(Mono.defer(() -> securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg), DESIGNATION, key)));
    }

    @Override
    protected Mono<Designation> updatableEntity(Designation entity) {
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

    public Mono<Map<ULong, Tuple2<AppRegistrationDesignation, Designation>>> createForRegistration(
            Client client,
            List<AppRegistrationDesignation> designations,
            Map<ULong, Tuple2<AppRegistrationDepartment, Department>> departmentIndex) {

        if (designations == null || designations.isEmpty())
            return Mono.just(Map.of());

        return this.dao.createForRegistration(client, designations, departmentIndex);
    }
}
