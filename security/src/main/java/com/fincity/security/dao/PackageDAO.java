package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Package;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;

@Component
public class PackageDAO extends AbstractClientCheckDAO<SecurityPackageRecord, ULong, com.fincity.security.dto.Package> {

	public PackageDAO() {
		super(Package.class, SECURITY_PACKAGE, SECURITY_PACKAGE.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {

		return SECURITY_PACKAGE.CLIENT_ID;
	}
}
