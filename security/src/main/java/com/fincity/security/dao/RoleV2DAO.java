package com.fincity.security.dao;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.SecurityV2Role;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;

@Component
public class RoleV2DAO extends AbstractClientCheckDAO<SecurityV2RoleRecord, ULong, RoleV2> {

    public RoleV2DAO() {
        super(RoleV2.class, SecurityV2Role.SECURITY_V2_ROLE, SecurityV2Role.SECURITY_V2_ROLE.CLIENT_ID);
    }

    @Override
    public Field<ULong> getClientIDField() {
        return SecurityV2Role.SECURITY_V2_ROLE.CLIENT_ID;
    }
}