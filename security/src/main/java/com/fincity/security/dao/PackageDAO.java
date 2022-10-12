package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;

import java.util.HashSet;
import java.util.Set;

import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Package;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;
import com.fincity.security.jooq.tables.records.SecurityPackageRoleRecord;

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

	public Mono<Boolean> checkRoleApplicableForPackage(ULong packageId, ULong roleId) {
		return Mono.just(this.dslContext.selectFrom(SECURITY_PACKAGE_ROLE)
		        .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)
		                .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)))
		        .execute() > 0);
	}

	public Mono<Integer> removeRole(ULong packageId, ULong roleId) {

		DeleteQuery<SecurityPackageRoleRecord> query = this.dslContext.deleteQuery(SECURITY_PACKAGE_ROLE);

		query.addConditions(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)
		        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)));

		return Mono.from(query);
	}

	public Mono<Boolean> checkRoleFromBasePackage(ULong roleId) {

		return Mono.from(this.dslContext.select(SECURITY_PACKAGE.ID)
		        .from(SECURITY_PACKAGE_ROLE)
		        .leftJoin(SECURITY_PACKAGE)
		        .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		        .where(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)
		                .and(SECURITY_PACKAGE.BASE.eq((byte) 1)))
		        .limit(1))
		        .map(Record1::value1)
		        .map(val -> val.intValue() > 0);
	}

	public Mono<Set<ULong>> getClientListFromPackage(ULong packageId, ULong roleId) {

		Set<ULong> clientList = new HashSet<>();

		Flux.from(

		        this.dslContext.select(SECURITY_CLIENT_PACKAGE.CLIENT_ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)
		                        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId))))
		        .map(Record1::value1)
		        .map(clientList::add);

		return Mono.just(clientList);
	}

	public Mono<Set<ULong>> getFilteredClientListFromDifferentPackage(ULong packageId, ULong roleId,
	        Set<ULong> clientList) {

		Set<ULong> differentPackageClientList = new HashSet<>();

		Set<ULong> filteredClientList = new HashSet<>();

		Flux.from(

		        this.dslContext.select(SECURITY_CLIENT_PACKAGE.CLIENT_ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId)
		                        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId))))
		        .map(Record1::value1)
		        .map(differentPackageClientList::add);

		clientList.stream()
		        .filter(client -> !differentPackageClientList.contains(client))
		        .forEach(filteredClientList::add);

		return Mono.just(filteredClientList);

	}

	public Mono<Set<ULong>> omitPermissionFromBasePackage(ULong roleId, Set<ULong> permissionList) {

		Set<ULong> basePermissionList = new HashSet<>();

		Set<ULong> filteredPermissionList = new HashSet<>();

		Flux.from(

		        this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		                .from(SECURITY_ROLE_PERMISSION)
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		                .leftJoin(SECURITY_PACKAGE)
		                .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .where(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId)
		                        .and(SECURITY_PACKAGE.BASE.eq((byte) 1)))

		)
		        .map(Record1::value1)
		        .map(basePermissionList::add);

		permissionList.stream()
		        .filter(permission -> !basePermissionList.contains(permission))
		        .forEach(filteredPermissionList::add);

		return Mono.just(filteredPermissionList);
	}

	public Mono<Set<ULong>> fetchClientsFromGivenPackage(ULong packageId) {

		Set<ULong> clientList = new HashSet<>();

		Flux.from(

		        this.dslContext.select(SECURITY_CLIENT_PACKAGE.CLIENT_ID)
		                .from(SECURITY_CLIENT_PACKAGE)
		                .where(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(packageId)))
		        .map(Record1::value1)
		        .map(clientList::add);

		return Mono.just(clientList);
	}

	public Mono<Set<ULong>> omitClientsFromDifferentPackage(ULong packageId, Set<ULong> clientList,
	        Set<ULong> permissionList) {

		Set<ULong> differentPackageClientList = new HashSet<>();

		Set<ULong> filteredClientList = new HashSet<>();

		Flux.from(

		        this.dslContext.select(SECURITY_CLIENT_PACKAGE.CLIENT_ID)
		                .from(SECURITY_ROLE_PERMISSION)
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId)
		                        .and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.in(permissionList))))
		        .map(Record1::value1)
		        .map(differentPackageClientList::add);

		clientList.stream()
		        .filter(client -> !differentPackageClientList.contains(client))
		        .forEach(filteredClientList::add);

		return Mono.just(filteredClientList);
	}
}
