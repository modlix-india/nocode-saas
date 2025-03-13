package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityDesignation.*;
import static com.fincity.security.jooq.tables.SecurityDepartment.*;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Designation;
import com.fincity.security.jooq.tables.records.SecurityDesignationRecord;

import reactor.core.publisher.Mono;

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
                        .where(SECURITY_DEPARTMENT.ID.eq(departmentId)).and(SECURITY_DEPARTMENT.CLIENT_ID.eq(clientId)))
                        .map(r -> r.value1() == 1);

        return Mono.zip(parentDesignation, nextDesignation, department)
                .map(tuple -> tuple.getT1() && tuple.getT2() && tuple.getT3());

    }
}