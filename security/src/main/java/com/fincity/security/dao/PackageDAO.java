package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Package;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;
import com.fincity.security.jooq.tables.records.SecurityPackageRoleRecord;
import com.fincity.security.jooq.tables.records.SecurityUserRolePermissionRecord;

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

	public Mono<Boolean> checkRoleAvailableForGivenPackage(ULong packageId, ULong roleId) { // update here

		Condition rolePackageCondition = SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)
		        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId));

		Condition packageCondition = SECURITY_PACKAGE.BASE.eq((byte) 1);

		return Mono.from(

		        this.dslContext.select(SECURITY_PACKAGE.ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_PACKAGE.ID))
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))
		                .where(rolePackageCondition.or(packageCondition))
		                .limit(1))
		        .map(Record1::value1)
		        .map(Objects::nonNull);
	}

	public Mono<Boolean> addRoleToPackage(ULong packageId, ULong roleId) {

		return Mono.from(

		        this.dslContext
		                .insertInto(SECURITY_PACKAGE_ROLE, SECURITY_PACKAGE_ROLE.PACKAGE_ID,
		                        SECURITY_PACKAGE_ROLE.ROLE_ID)
		                .values(packageId, roleId))
		        .map(e -> e > 0);
	}

	public Mono<Boolean> checkRoleAssignedForPackage(ULong packageId, ULong roleId) {

		return Mono.from(

		        this.dslContext.selectCount()
		                .from(SECURITY_PACKAGE_ROLE)
		                .where(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)
		                        .and(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId))))
		        .map(Record1::value1)
		        .map(count -> count > 0);
	}

	public Mono<Boolean> removeRole(ULong packageId, ULong roleId) {

		DeleteQuery<SecurityPackageRoleRecord> query = this.dslContext.deleteQuery(SECURITY_PACKAGE_ROLE);

		query.addConditions(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)
		        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)));

		return Mono.from(query)
		        .map(value -> value > 0);
	}

	public Mono<Boolean> checkRoleFromBasePackage(ULong packageId, ULong roleId) {

		return Mono.from(

		        this.dslContext.selectCount()
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))
		                .where(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)
		                        .and(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId))
		                        .and(SECURITY_PACKAGE.BASE.eq((byte) 1))))
		        .map(Record1::value1)
		        .map(count -> count > 0);

	}

	public Mono<List<ULong>> getUsersListFromPackage(ULong packageId) {

		return Flux.from(

		        this.dslContext.select(SECURITY_USER.ID)
		                .from(SECURITY_CLIENT_PACKAGE)
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .where(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(packageId)))
		        .map(Record1::value1)
		        .collectList();
	}

	public Mono<List<ULong>> getUsersListFromPackageForOtherRole(ULong packageId, ULong roleId) {

		return Flux.from(

		        this.dslContext.select(SECURITY_USER.ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId)
		                        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId))))
		        .map(Record1::value1)
		        .collectList();
	}

	public Mono<Boolean> removeRoleFromUsers(ULong roleId, List<ULong> userIds) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
		        .deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.eq(roleId)
		        .and(SECURITY_USER_ROLE_PERMISSION.USER_ID.in(userIds)));

		return Mono.from(query)
		        .map(count -> count > 0);
	}

	public Mono<List<ULong>> getPermissionsFromBasePackage(ULong packageId, List<ULong> permissions) {

		// update require here

		return Flux.from(this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		        .from(SECURITY_ROLE_PERMISSION)
		        .leftJoin(SECURITY_PACKAGE_ROLE)
		        .on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
		        .leftJoin(SECURITY_PACKAGE)
		        .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID)))
		        .map(Record1::value1)
		        .collectList();
	}

}
