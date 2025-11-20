package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityDepartment.*;
import static com.fincity.security.jooq.tables.SecurityDesignation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.Designation;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationDesignation;
import com.fincity.security.jooq.tables.records.SecurityDesignationRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class DesignationDAO extends AbstractClientCheckDAO<SecurityDesignationRecord, ULong, Designation> {

    protected DesignationDAO() {
        super(Designation.class, SECURITY_DESIGNATION, SECURITY_DESIGNATION.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_DESIGNATION.CLIENT_ID;
    }

    public Mono<Boolean> checkSameClient(ULong clientId, ULong parentDesignationId, ULong nextDesignationId,
                                         ULong departmentId) {
        Mono<Boolean> parentDesignation = parentDesignationId == null ? Mono.just(true)
                : Mono.from(this.dslContext.selectCount()
                        .from(SECURITY_DESIGNATION)
                        .where(SECURITY_DESIGNATION.ID.eq(parentDesignationId))
                        .and(SECURITY_DESIGNATION.CLIENT_ID.eq(clientId)))
                .map(r -> r.value1() == 1);

        Mono<Boolean> nextDesignation = nextDesignationId == null ? Mono.just(true)
                : Mono.from(this.dslContext.selectCount()
                        .from(SECURITY_DESIGNATION)
                        .where(SECURITY_DESIGNATION.ID.eq(nextDesignationId))
                        .and(SECURITY_DESIGNATION.CLIENT_ID.eq(clientId)))
                .map(r -> r.value1() == 1);

        Mono<Boolean> department = departmentId == null ? Mono.just(true)
                : Mono.from(this.dslContext.selectCount()
                        .from(SECURITY_DEPARTMENT)
                        .where(SECURITY_DEPARTMENT.ID.eq(departmentId))
                        .and(SECURITY_DEPARTMENT.CLIENT_ID.eq(clientId)))
                .map(r -> r.value1() == 1);

        return Mono.zip(parentDesignation, nextDesignation, department)
                .map(tuple -> tuple.getT1() && tuple.getT2() && tuple.getT3());

    }

    public Mono<Boolean> canAssignDesignation(ULong clientId, ULong designationId) {
        return Mono.from(this.dslContext.selectCount().from(SECURITY_DESIGNATION).where(
                DSL.and(
                        SECURITY_DESIGNATION.ID.eq(designationId),
                        SECURITY_DESIGNATION.CLIENT_ID.eq(clientId)
                )
        )).map(r -> r.value1() == 1);
    }

    record DesignationRelation(ULong designationId, ULong parentDesignationId, ULong nextDesignationId) {
    }

    private Mono<Boolean> createRelations(List<DesignationRelation> relations) {
        return Flux.fromIterable(relations).flatMap(tup -> Mono.from(
                        this.dslContext.update(SECURITY_DESIGNATION)
                                .set(SECURITY_DESIGNATION.PARENT_DESIGNATION_ID,
                                        tup.parentDesignationId())
                                .set(SECURITY_DESIGNATION.NEXT_DESIGNATION_ID, tup.nextDesignationId())
                                .where(SECURITY_DESIGNATION.ID.eq(tup.designationId()))))
                .collectList().map(list -> true);
    }

    public Mono<Map<ULong, Tuple2<AppRegistrationDesignation, Designation>>> createForRegistration(Client client,
                                                                                                   List<AppRegistrationDesignation> departments,
                                                                                                   Map<ULong, Tuple2<AppRegistrationDepartment, Department>> departmentIndex) {

        return FlatMapUtil.flatMapMono(
                        () -> Flux.fromIterable(departments)
                                .flatMap(e -> this.create(new Designation().setClientId(client.getId())
                                                .setName(e.getName())
                                                .setDescription(e.getDescription())
                                                .setDepartmentId(e.getDepartmentId() == null ? null
                                                        : departmentIndex.get(e.getDepartmentId()).getT2().getId()))
                                        .map(d -> Tuples.of(e, d)))
                                .collectList(),

                        list -> {
                            Map<ULong, Tuple2<AppRegistrationDesignation, Designation>> index = list.stream()
                                    .collect(Collectors.toMap(
                                            e -> e.getT1().getId(), Function.identity()));

                            List<DesignationRelation> relations = new ArrayList<>();
                            for (Tuple2<AppRegistrationDesignation, Designation> tuple : list) {
                                if (tuple.getT1().getParentDesignationId() == null
                                        && tuple.getT1().getNextDesignationId() == null)
                                    continue;

                                var parent = index.get(tuple.getT1().getParentDesignationId());
                                var next = index.get(tuple.getT1().getNextDesignationId());
                                relations.add(new DesignationRelation(tuple.getT2().getId(),
                                        parent == null ? null
                                                : parent.getT2()
                                                .getId(),
                                        next == null ? null
                                                : next.getT2()
                                                .getId()));
                            }

                            if (relations.isEmpty())
                                return Mono.just(index);

                            return this.createRelations(relations)
                                    .map(x -> index);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DesignationDAO.createForRegistration"));
    }

    public Mono<Designation> readInternal(ULong id) {
        return Mono.from(this.dslContext.selectFrom(this.table)
                        .where(this.idField.eq(id))
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }
}