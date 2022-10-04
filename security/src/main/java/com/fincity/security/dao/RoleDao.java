package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;

import reactor.core.publisher.Mono;

@Component
public class RoleDao extends AbstractClientCheckDAO<SecurityRoleRecord, ULong, Role> {

	public RoleDao() {
		super(Role.class, SECURITY_ROLE, SECURITY_ROLE.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_ROLE.CLIENT_ID;
	}

	public Mono<ULong> getClientIdFromRoleAndPermission(ULong roleId, ULong permissionId) {

		return Mono.from(

		        this.dslContext.select(SECURITY_CLIENT_PACKAGE.CLIENT_ID)
		                .from(SECURITY_ROLE_PERMISSION)
		                .leftJoin(SECURITY_PACKAGE_ROLE)
		                .on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE_PERMISSION.ROLE_ID))
		                .leftJoin(SECURITY_CLIENT_PACKAGE)
		                .on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
		                .where(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId)
		                        .and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)))
		                .limit(1))
		        .map(Record1::value1);

	}

	public Mono<Boolean> isRoleAndPermissionCreatedBySameClient(ULong roleId, ULong permissionId) {

		return flatMapMono(

		        () -> Mono.from(

		                this.dslContext.select(SECURITY_PERMISSION.CLIENT_ID)
		                        .from(SECURITY_PERMISSION)
		                        .where(SECURITY_PERMISSION.ID.eq(permissionId))
		                        .limit(1))
		                .map(Record1::value1),

		        permissionClient -> Mono.from(

		                this.dslContext.select(SECURITY_ROLE.CLIENT_ID)
		                        .from(SECURITY_ROLE)
		                        .where(SECURITY_ROLE.ID.eq(roleId))
		                        .limit(1))
		                .map(Record1::value1),

		        (permissionClient, roleClient) -> permissionClient.equals(roleClient) ? Mono.just(true)
		                : Mono.just(false)

		);

	}

	public Mono<Integer> addPermission(ULong roleId, ULong permissionId) {

		return Mono.from(

		        this.dslContext
		                .insertInto(SECURITY_ROLE_PERMISSION, SECURITY_ROLE_PERMISSION.ROLE_ID,
		                        SECURITY_ROLE_PERMISSION.PERMISSION_ID)
		                .values(roleId, permissionId));

	}
}
