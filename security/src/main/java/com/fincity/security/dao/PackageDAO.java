package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;

import java.util.HashSet;
import java.util.Set;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Package;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PackageDAO extends AbstractClientCheckDAO<SecurityPackageRecord, ULong, com.fincity.security.dto.Package> {

	public PackageDAO() {
		super(Package.class, SECURITY_PACKAGE, SECURITY_PACKAGE.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {

		return SECURITY_PACKAGE.CLIENT_ID;
	}

	public Mono<ULong> getClientIdFromPackage(ULong packageId) {
		return Mono.from(

		        this.dslContext.select(SECURITY_PACKAGE.CLIENT_ID)
		                .from(SECURITY_PACKAGE)
		                .where(SECURITY_PACKAGE.ID.eq(packageId))
		                .limit(1)

		)
		        .map(Record1::value1);
	}

	public Mono<Set<ULong>> getRolesFromPackage(ULong packageId) {

		Set<ULong> roles = new HashSet<>();

		Flux.from(this.dslContext.select(SECURITY_PACKAGE_ROLE.ROLE_ID)
		        .from(SECURITY_PACKAGE_ROLE)
		        .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)))
		        .map(Record1::value1)
		        .map(role -> roles.add(role));

		return Mono.just(roles);
	}

	public Mono<Set<ULong>> getRolesAfterOmittingFromBasePackage(Set<ULong> roles) {

		Set<ULong> baseRoles = new HashSet<>();

		Set<ULong> roleAfterOmitting = new HashSet<>();

		Flux.from(this.dslContext.select(SECURITY_ROLE.ID)
		        .from(SECURITY_ROLE)
		        .leftJoin(SECURITY_PACKAGE_ROLE)
		        .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE.ID))
		        .leftJoin(SECURITY_PACKAGE)
		        .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		        .where(SECURITY_PACKAGE.BASE.eq((byte) 1)))
		        .map(Record1::value1)
		        .map(role -> baseRoles.add(role));

		roles.stream()
		        .filter(role -> !baseRoles.contains(role))
		        .forEach(roleAfterOmitting::add);

		return Mono.just(roleAfterOmitting);
	}

	public Mono<Set<ULong>> getPermissionsFromPackage(ULong packageId) {

		Set<ULong> permissionList = new HashSet<>();

		Flux.from(this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		        .from(SECURITY_PACKAGE)
		        .leftJoin(SECURITY_PACKAGE_ROLE)
		        .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		        .leftJoin(SECURITY_ROLE_PERMISSION)
		        .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		        .where(SECURITY_PACKAGE.ID.eq(packageId)))
		        .map(Record1::value1)
		        .map(permissionList::add);

		return Mono.just(permissionList);
	}

	public Mono<Set<ULong>> omitPermissionsFromBasePackage(Set<ULong> permissions) {

		Set<ULong> permissionList = new HashSet<>();

		Set<ULong> filterPermissions = new HashSet<>();

		Flux.from(this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		        .from(SECURITY_PACKAGE)
		        .leftJoin(SECURITY_PACKAGE_ROLE)
		        .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		        .leftJoin(SECURITY_ROLE_PERMISSION)
		        .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		        .where(SECURITY_PACKAGE.BASE.eq((byte) 1)))
		        .map(Record1::value1)
		        .map(permissionList::add);

		permissions.stream()
		        .filter(per -> !permissionList.contains(per))
		        .forEach(filterPermissions::add);

		return Mono.just(filterPermissions);
	}
}
