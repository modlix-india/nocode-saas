package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityDepartment.*;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Department;
import com.fincity.security.jooq.tables.records.SecurityDepartmentRecord;

import reactor.core.publisher.Mono;

@Component
public class DepartmentDAO extends AbstractClientCheckDAO<SecurityDepartmentRecord, ULong, Department> {

    protected DepartmentDAO() {
        super(Department.class, SECURITY_DEPARTMENT, SECURITY_DEPARTMENT.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_DEPARTMENT.CLIENT_ID;
    }

    public Mono<Boolean> checkSameClient(ULong clientId, ULong departmentId) {
        return Mono.from(this.dslContext.selectCount()
                .from(SECURITY_DEPARTMENT)
                .where(SECURITY_DEPARTMENT.ID.eq(departmentId)).and(SECURITY_DEPARTMENT.CLIENT_ID.eq(clientId)))
                .map(r -> r.value1() == 1);
    }
}
