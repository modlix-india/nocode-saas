package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientPackage.SECURITY_CLIENT_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackage.SECURITY_PACKAGE;
import static com.fincity.security.jooq.tables.SecurityPackageRole.SECURITY_PACKAGE_ROLE;
import static com.fincity.security.jooq.tables.SecurityPermission.SECURITY_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityRole.SECURITY_ROLE;
import static com.fincity.security.jooq.tables.SecurityRolePermission.SECURITY_ROLE_PERMISSION;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityRolePermissionRecord;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;
import com.fincity.security.jooq.tables.records.SecurityUserRolePermissionRecord;
import com.fincity.security.model.TransportPOJO.AppTransportPermission;
import com.fincity.security.model.TransportPOJO.AppTransportRole;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class RoleDAO extends AbstractClientCheckDAO<SecurityRoleRecord, ULong, Role> {

	public RoleDAO() {
		super(Role.class, SECURITY_ROLE, SECURITY_ROLE.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_ROLE.CLIENT_ID;
	}

	public Mono<Boolean> checkPermissionExistsForRole(ULong roleId, ULong permissionId) {

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_ROLE_PERMISSION)
						.where(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId)
								.and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId))))
				.map(Record1::value1)
				.map(val -> val == 1);
	}

	public Mono<Permission> getPermissionRecord(ULong permissionId) {

		return Mono.from(

				this.dslContext.select(SECURITY_PERMISSION.fields())
						.from(SECURITY_PERMISSION)
						.where(SECURITY_PERMISSION.ID.eq(permissionId))
						.limit(1))
				.map(e -> e.into(Permission.class));
	}

	public Mono<Boolean> addPermission(ULong roleId, ULong permissionId) {

		return Mono.from(

				this.dslContext
						.insertInto(SECURITY_ROLE_PERMISSION, SECURITY_ROLE_PERMISSION.ROLE_ID,
								SECURITY_ROLE_PERMISSION.PERMISSION_ID)
						.values(roleId, permissionId))
				.map(e -> e > 0);

	}

	public Mono<Boolean> checkPermissionAvailableForGivenRole(ULong roleId, ULong permissionId) {

		Condition rolePermissionCondition = SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)
				.and(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId));

		Condition packageCondition = SECURITY_PACKAGE.BASE.eq((byte) 1);

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_ROLE)
						.leftJoin(SECURITY_ROLE_PERMISSION)
						.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_ROLE.ID))
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_ROLE.ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
						.leftJoin(SECURITY_PACKAGE)
						.on(SECURITY_PACKAGE.ID.eq(SECURITY_PACKAGE_ROLE.PACKAGE_ID))
						.leftJoin(SECURITY_CLIENT_PACKAGE)
						.on(SECURITY_CLIENT_PACKAGE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))
						.leftJoin(SECURITY_CLIENT)
						.on(SECURITY_CLIENT.ID.eq(SECURITY_PACKAGE.CLIENT_ID))
						.where(rolePermissionCondition.or(packageCondition))
						.limit(1))
				.map(Record1::value1)
				.map(value -> value > 0);

	}

	public Mono<Boolean> removePermissionFromRole(ULong roleId, ULong permissionId) {

		DeleteQuery<SecurityRolePermissionRecord> query = this.dslContext.deleteQuery(SECURITY_ROLE_PERMISSION);

		query.addConditions(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId)
				.and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)));

		return Mono.from(query)
				.map(value -> value == 1);
	}

	public Mono<Boolean> checkPermissionBelongsToBasePackage(ULong permissionId) {

		return Mono.from(

				this.dslContext.selectCount()
						.from(SECURITY_ROLE_PERMISSION)
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
						.leftJoin(SECURITY_PACKAGE)
						.on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_PACKAGE.ID))
						.where(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)
								.and(SECURITY_PACKAGE.BASE.eq((byte) 1))))
				.map(Record1::value1)
				.map(value -> value == 1);

	}

	@Override
	protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

		return SecurityContextUtil.getUsersContextAuthentication()
				.map(ca -> {

					SelectJoinStep<Record> mainQuery = dslContext.select(Arrays.asList(table.fields()))
							.select(DSL
									.replace(DSL.concat("Authorities.", DSL.concat(
											DSL.if_(SECURITY_APP.APP_CODE.isNull(), "",
													DSL.concat(DSL.upper(SECURITY_APP.APP_CODE), ".")),
											DSL.concat("ROLE_", SECURITY_ROLE.NAME))),
											" ", "_")
									.as("AUTHORITY"))
							.from(table)
							.leftJoin(SECURITY_APP)
							.on(SECURITY_APP.ID.eq(SECURITY_ROLE.APP_ID));

					SelectJoinStep<Record1<Integer>> countQuery = dslContext.select(DSL.count())
							.from(table);

					if (ca.getClientTypeCode()
							.equals(ContextAuthentication.CLIENT_TYPE_SYSTEM))
						return Tuples.of(mainQuery, countQuery);

					return this.addJoinCondition(mainQuery, countQuery, this.getClientIDField());
				});
	}

	public Mono<List<ULong>> getUsersListFromRole(ULong roleId) {

		return Flux.from(this.dslContext.selectDistinct(SECURITY_USER.ID)
				.from(SECURITY_PACKAGE_ROLE)
				.leftJoin(SECURITY_CLIENT_PACKAGE)
				.on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
				.leftJoin(SECURITY_USER)
				.on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
				.where(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(roleId)))
				.collectList()
				.flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<>())
						: Mono.just(records.stream()
								.map(Record1::value1)
								.filter(Objects::nonNull)
								.toList())

				);

	}

	public Mono<List<ULong>> getUsersListFromClient(ULong clientId) {

		return Flux.from(this.dslContext.select(SECURITY_USER.ID)
				.from(SECURITY_USER)
				.where(SECURITY_USER.CLIENT_ID.eq(clientId)))
				.map(Record1::value1)
				.collectList();
	}

	public Mono<List<ULong>> omitUsersListFromDifferentRole(ULong roleId, ULong permissionId,
			List<ULong> permissionUsers) {

		return Flux.from(

				this.dslContext.selectDistinct(SECURITY_USER.ID)
						.from(SECURITY_ROLE_PERMISSION)
						.leftJoin(SECURITY_PACKAGE_ROLE)
						.on(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(SECURITY_PACKAGE_ROLE.ROLE_ID))
						.leftJoin(SECURITY_CLIENT_PACKAGE)
						.on(SECURITY_PACKAGE_ROLE.PACKAGE_ID.eq(SECURITY_CLIENT_PACKAGE.PACKAGE_ID))
						.leftJoin(SECURITY_USER)
						.on(SECURITY_CLIENT_PACKAGE.CLIENT_ID.eq(SECURITY_USER.CLIENT_ID))
						.where(SECURITY_ROLE_PERMISSION.ROLE_ID.ne(roleId)
								.and(SECURITY_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId))))
				.collectList()
				.flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<>())
						: Mono.just(records.stream()
								.map(Record1::value1)
								.filter(Objects::nonNull)
								.toList()))
				.map(omittedPermissionUsers -> {
					List<ULong> users = new ArrayList<>(permissionUsers);

					if (omittedPermissionUsers.isEmpty())
						omittedPermissionUsers.forEach(users::remove);

					return users;

				});

	}

	public Mono<Boolean> removePemissionFromUsers(ULong permissionId, List<ULong> users) {

		DeleteQuery<SecurityUserRolePermissionRecord> query = this.dslContext
				.deleteQuery(SECURITY_USER_ROLE_PERMISSION);

		query.addConditions(SECURITY_USER_ROLE_PERMISSION.PERMISSION_ID.eq(permissionId)
				.and(SECURITY_USER_ROLE_PERMISSION.USER_ID.in(users)));

		return Mono.from(query)
				.map(result -> result > 0);

	}

	public Mono<List<ULong>> getPermissionsFromRole(ULong roleId) {

		return Flux.from(

				this.dslContext.select(SECURITY_ROLE_PERMISSION.PERMISSION_ID)
						.from(SECURITY_ROLE_PERMISSION)
						.where(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId)))
				.collectList()
				.flatMap(records -> records == null || records.isEmpty() ? Mono.just(new ArrayList<ULong>())
						: Mono.just(records.stream()
								.map(Record1::value1)
								.filter(Objects::nonNull)
								.toList()));
	}

	public Mono<List<Permission>> getPermissionsFromGivenRole(ULong roleId) {

		return Flux.from(

				this.dslContext.select(SECURITY_PERMISSION.fields())
						.from(SECURITY_ROLE_PERMISSION)

						.leftJoin(SECURITY_PERMISSION)
						.on(SECURITY_PERMISSION.ID.eq(SECURITY_ROLE_PERMISSION.PERMISSION_ID))

						.where(SECURITY_ROLE_PERMISSION.ROLE_ID.eq(roleId))

		)
				.map(e -> e.into(Permission.class))
				.collectList();

	}

	public Mono<Map<ULong, Collection<String>>> getRoleNamesFromPackagesForTransport(List<ULong> packages, ULong appId,
			ULong appClientId, ULong clientId) {

		return Flux.from(

				this.dslContext.select(SECURITY_PACKAGE_ROLE.PACKAGE_ID, SECURITY_ROLE.NAME)
						.from(SECURITY_PACKAGE_ROLE)
						.leftJoin(SECURITY_ROLE).on(SECURITY_PACKAGE_ROLE.ROLE_ID.eq(SECURITY_ROLE.ID))
						.where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.in(packages).and(SECURITY_ROLE.APP_ID.eq(appId))
								.and(SECURITY_ROLE.CLIENT_ID.eq(appClientId).or(SECURITY_ROLE.CLIENT_ID.eq(clientId))))

		).map(e -> Tuples.of(e.get(SECURITY_PACKAGE_ROLE.PACKAGE_ID), e.get(SECURITY_ROLE.NAME)))
				.collectMultimap(Tuple2::getT1, Tuple2::getT2);

	}

	public Mono<List<AppTransportRole>> readForTransport(ULong appId, ULong appClientId, ULong clientId) {

		return FlatMapUtil.flatMapMono(

				() -> Flux.from(this.dslContext.selectFrom(SECURITY_ROLE).where(SECURITY_ROLE.APP_ID.eq(appId)
						.and(SECURITY_ROLE.CLIENT_ID.eq(appClientId).or(SECURITY_ROLE.CLIENT_ID.eq(clientId)))))
						.collectList(),

				roles -> Flux
						.from(this.dslContext
								.select(SECURITY_ROLE_PERMISSION.ROLE_ID, SECURITY_PERMISSION.NAME,
										SECURITY_PERMISSION.DESCRIPTION)
								.from(SECURITY_ROLE_PERMISSION)
								.leftJoin(SECURITY_PERMISSION)
								.on(SECURITY_PERMISSION.ID.eq(SECURITY_ROLE_PERMISSION.PERMISSION_ID))
								.where(SECURITY_ROLE_PERMISSION.ROLE_ID
										.in(roles.stream().map(SecurityRoleRecord::getId).toList())
										.and(SECURITY_PERMISSION.APP_ID.eq(appId))
										.and(SECURITY_PERMISSION.CLIENT_ID.eq(appClientId)
												.or(SECURITY_PERMISSION.CLIENT_ID.eq(clientId)))))
						.collectList(),

				(roles, rolePermissions) -> {

					Map<ULong, List<Tuple2<ULong, AppTransportPermission>>> rolePermissionMap = rolePermissions.stream()
							.map(e -> Tuples.of(e.get(SECURITY_ROLE_PERMISSION.ROLE_ID),
									new AppTransportPermission().setPermissionName(e.get(SECURITY_PERMISSION.NAME))
											.setPermissionDescription(e.get(SECURITY_PERMISSION.DESCRIPTION))))
							.collect(Collectors.groupingBy(Tuple2::getT1));

					return Mono.just(roles.stream()
							.map(e -> new AppTransportRole().setRoleName(e.get(SECURITY_ROLE.NAME))
									.setRoleDescription(e.get(SECURITY_ROLE.DESCRIPTION))
									.setPermissions(rolePermissionMap.get(e.getId()) == null ? null
											: rolePermissionMap
													.get(e.getId())
													.stream()
													.map(Tuple2::getT2)
													.toList()))
							.toList());

				});
	}

	public Mono<List<Role>> getRolesByNamesAndAppId(List<String> names, ULong appId) {

		return Flux.from(
				this.dslContext.selectFrom(SECURITY_ROLE)
						.where(SECURITY_ROLE.NAME.in(names).and(SECURITY_ROLE.APP_ID.eq(appId))))
				.map(e -> e.into(Role.class))
				.collectList();
	}

	public Mono<List<Role>> createRolesFromTransport(List<Role> roles) {

		return Flux.fromIterable(roles)
				.flatMap(this::create)
				.collectList();
	}

	public Mono<Boolean> createPackageRoles(Map<ULong, ULong> packageRoles) {

		return FlatMapUtil.flatMapMono(

				() -> Mono.from(this.dslContext.deleteFrom(SECURITY_PACKAGE_ROLE)
						.where(SECURITY_PACKAGE_ROLE.PACKAGE_ID.in(packageRoles.keySet()))),

				delCount -> Mono.from(
						this.dslContext
								.insertInto(SECURITY_PACKAGE_ROLE, SECURITY_PACKAGE_ROLE.PACKAGE_ID,
										SECURITY_PACKAGE_ROLE.ROLE_ID)
								.values(packageRoles.entrySet().stream()
										.map(e -> DSL.row(e.getKey(), e.getValue())).toList()))
						.map(e -> true));
	}
}
