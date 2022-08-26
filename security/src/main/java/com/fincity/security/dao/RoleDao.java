package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;

public class RoleDao extends AbstractUpdatableDAO<SecurityRoleRecord, ULong, Role> {

	public RoleDao() {
		super(Role.class, SECURITY_ROLE, SECURITY_ROLE.ID);
	}

}
