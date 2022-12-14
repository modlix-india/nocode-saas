package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
		        .map(count -> count == 1);
	}

	public Mono<Boolean> removeRole(ULong packageId, ULong roleId) {

		DeleteQuery<SecurityPackageRoleRecord> query = this.dslContext.deleteQuery(SECURITY_PACKAGE_ROLE);

		query.addConditions(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)
		        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)));

		return Mono.from(query)
		        .map(value -> value == 1);
	}

	public Mono<Boolean> checkRoleFromBasePackage(ULong roleId) {

		return Mono.from(

		        this.dslContext.selectCount()
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))
		                .where(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)
		                        .and(SECURITY_PACKAGE.BASE.eq((byte) 1))))
		        .map(Record1::value1)
		        .map(count -> count > 0);

	}

	public Mono<List<ULong>> getUsersListFromPackage(ULong packageId) {

		return Flux.from(

		        this.dslContext.selectDistinct(SECURITY_USER.ID)
		                .from(SECURITY_CLIENT_PACKAGE)
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .leftJoin(SECURITY_PACKAGE)
		                .on(SECURITY_USER.CLIENT_ID.eq(SECURITY_PACKAGE.CLIENT_ID))
		                .where(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(packageId)
		                        .or(SECURITY_PACKAGE.ID.eq(packageId))))
		        .collectList()
		        .flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<ULong>())

		                : Mono.just(records.stream()
		                        .map(Record1::value1)
		                        .filter(Objects::nonNull)
		                        .toList()));
	}

	public Mono<List<ULong>> getUsersListFromRoleForOtherPackages(ULong packageId, ULong roleId) {

		return Flux.from(

		        this.dslContext.select(SECURITY_USER.ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId)
		                        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId))))
		        .collectList()
		        .flatMap(records -> records == null || records.isEmpty() ? Mono.just(List.of())
		                : Mono.just(records.stream()
		                        .map(Record1::value1)
		                        .filter(Objects::nonNull)
		                        .toList()));
	}

	public Mono<List<ULong>> getUsersListFromRoleForOtherPackages(ULong packageId, ULong roleId, List<ULong> users) {

		return Flux.from(

		        this.dslContext.select(SECURITY_USER.ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId)
		                        .and(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId))))
		        .collectList()
		        .flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<ULong>())
		                : Mono.just(records.stream()
		                        .map(Record1::value1)
		                        .filter(Objects::nonNull)
		                        .toList()))
		        .map(omitUsers ->
				{
			        ArrayList<ULong> iUsers = new ArrayList<>(users);
			        if (!omitUsers.isEmpty())
				        omitUsers.forEach(iUsers::remove);

			        return iUsers;
		        });

	}

	public Mono<List<ULong>> removeUsersWithPermissionsFromRoleForOtherPackages(ULong packageId,
	        List<ULong> permissions, List<ULong> users) {

		return Flux.from(

		        this.dslContext.select(SECURITY_USER.ID)
		                .from(SECURITY_ROLE_PERMISSION)
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId)
		                        .and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.in(permissions))))
		        .collectList()
		        .flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<ULong>())
		                : Mono.just(records.stream()
		                        .map(Record1::value1)
		                        .filter(Objects::nonNull)
		                        .toList()))
		        .map(omitUsers ->
				{
			        ArrayList<ULong> iUsers = new ArrayList<>(users);
			        if (!omitUsers.isEmpty())
				        omitUsers.forEach(iUsers::remove);

			        return iUsers;
		        });
	}

	public Mono<Boolean> removeRoleFromUsers(ULong roleId, List<ULong> userIds) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
		        .deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.ROLE_ID.eq(roleId)
		        .and(SECURITY_USER_ROLE_PERMISSION.USER_ID.in(userIds)));

		return Mono.from(query)
		        .map(count -> count > 0);
	}

	public Mono<List<ULong>> removePermissionsPartOfBasePackage(List<ULong> permissions) {

		return Flux.from(this.dslContext.selectDistinct(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		        .from(SECURITY_ROLE_PERMISSION)
		        .leftJoin(SECURITY_PACKAGE_ROLE)
		        .on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
		        .leftJoin(SECURITY_PACKAGE)
		        .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))
		        .where(SECURITY_PACKAGE.BASE.eq((byte) 1))
		        .and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.in(permissions)))
		        .collectList()
		        .flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<ULong>())
		                : Mono.just(records.stream()
		                        .map(Record1::value1)
		                        .filter(Objects::nonNull)
		                        .toList()))
		        .map(basePermissions ->
				{

			        ArrayList<ULong> iPermissions = new ArrayList<>(permissions);

			        if (!basePermissions.isEmpty())
				        basePermissions.forEach(iPermissions::remove);

			        return iPermissions;
		        });

	}

	public Mono<List<ULong>> getUsersFromPermissionsListWithDifferentPackage(ULong packageId, List<ULong> permissions) {

		return Flux.from(

		        this.dslContext.select(SECURITY_USER.ID)
		                .from(SECURITY_ROLE_PERMISSION)
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
		                .leftJoin(SECURITY_USER)
		                .on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
		                .where(SECURITY_ROLE_PERMISSION.PERMISSION_ID.in(permissions)
		                        .and(SECURITY_PACKAGE_ROLE.PACKAGE_ID.ne(packageId))))
		        .collectList()
		        .flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<ULong>())
		                : Mono.just(records.stream()
		                        .map(Record1::value1)
		                        .filter(Objects::nonNull)
		                        .toList()))
		        .map(basePermissions ->
				{

			        ArrayList<ULong> iPermissions = new ArrayList<>(permissions);

			        if (!basePermissions.isEmpty())
				        basePermissions.forEach(iPermissions::remove);

			        return iPermissions;
		        });
	}

	public Mono<List<ULong>> getRolesFromPackage(ULong packageId) {

		return Flux.from(

		        this.dslContext.selectDistinct(SECURITY_PACKAGE_ROLE.ROLE_ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId)))
		        .map(Record1::value1)
		        .collectList();

	}

	public Mono<List<ULong>> omitRolesFromBasePackage(List<ULong> roles) {

		return Flux.from(this.dslContext.select(SECURITY_ROLE.ID)
		        .from(SECURITY_ROLE)
		        .leftJoin(SECURITY_PACKAGE_ROLE)
		        .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE.ID))
		        .leftJoin(SECURITY_PACKAGE)
		        .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		        .where(SECURITY_PACKAGE.BASE.eq((byte) 1)))
		        .map(Record1::value1)
		        .collectList()
		        .flatMap(otherRoles ->
				{
			        if (roles == null || otherRoles == null)
				        return Mono.just(List.of());

			        roles.removeAll(otherRoles); // edit here
			        return Mono.just(roles);
		        });

	}

	public Mono<List<ULong>> getPermissionsFromPackage(ULong packageId, List<ULong> roles) {

		return Flux.from(

		        this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		                .from(SECURITY_PACKAGE_ROLE)
		                .leftJoin(SECURITY_ROLE_PERMISSION)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		                .where(SECURITY_PACKAGE_ROLE.ROLE_ID.in(roles)
		                        .and(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(packageId))))
		        .map(Record1::value1)
		        .collectList();
	}

	public Mono<List<ULong>> omitPermissionsFromBasePackage(List<ULong> permissions) {

		return Flux.from(

		        this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		                .from(SECURITY_PACKAGE)
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .leftJoin(SECURITY_ROLE_PERMISSION)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		                .where(SECURITY_ROLE_PERMISSION.PERMISSION_ID.in(permissions)
		                        .and(SECURITY_PACKAGE.BASE.eq((byte) 1))))
		        .map(Record1::value1)
		        .collectList()
		        .flatMap(otherPermissions ->
				{
			        if (permissions == null || otherPermissions == null)
				        return Mono.just(List.of());

			        permissions.removeAll(otherPermissions);
			        return Mono.just(permissions);
		        });

	}
}
