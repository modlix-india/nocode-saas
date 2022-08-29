package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;

@Component
public class RoleDao extends AbstractClientCheckDAO<SecurityRoleRecord, ULong, Role> {

	public RoleDao() {
		super(Role.class, SECURITY_ROLE, SECURITY_ROLE.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_ROLE.CLIENT_ID;
	}

}
