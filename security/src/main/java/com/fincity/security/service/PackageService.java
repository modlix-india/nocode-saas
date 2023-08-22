package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.exception.DataAccessException;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.PackageDAO;
import com.fincity.security.dto.Package;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PackageService extends
		AbstractSecurityUpdatableDataService<SecurityPackageRecord, ULong, com.fincity.security.dto.Package, PackageDAO> {

	private static final String BASE = "base";

	private static final String CODE = "code";

	private static final String DESCRIPTION = "description";

	private static final String NAME = "name";

	private static final String PACKAGE = "package";

	private static final String ASSIGNED_ROLE = " Role is assigned to Package ";

	private static final String UNASSIGNED_ROLE = " Role is removed from Package ";

	@Autowired
	private ClientService clientService;

	@Autowired
	private RoleService roleService;

	@Autowired
	private UserService userService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.PACKAGE;
	}

	@PreAuthorize("hasAuthority('Authorities.Package_CREATE')")
	@Override
	public Mono<Package> create(Package entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

					if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())) {
						return super.create(entity);
					}

					entity.setBase(false);

					ULong userClientId = ULongUtil.valueOf(ca.getUser()
							.getClientId());

					if (entity.getClientId() == null || userClientId.equals(entity.getClientId())) {
						entity.setClientId(userClientId);
						return super.create(entity);
					}

					return clientService.isBeingManagedBy(userClientId, entity.getClientId())
							.flatMap(managed -> {
								if (managed.booleanValue())
									return super.create(entity);

								return Mono.empty();
							})
							.switchIfEmpty(Mono.defer(() -> securityMessageResourceService
									.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE)
									.flatMap(msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN,
											StringFormatter.format(msg, "User"))))));
				});

	}

	@PreAuthorize("hasAuthority('Authorities.Package_READ')")
	@Override
	public Mono<Package> read(ULong id) {
		return super.read(id);
	}

	@PreAuthorize("hasAuthority('Authorities.Package_READ')")
	public Flux<Package> readFilterWithReadPermission(AbstractCondition cond) {
		return super.readAllFilter(cond);
	}

	@PreAuthorize("hasAuthority('Authorities.Package_READ')")
	@Override
	public Mono<Page<Package>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasAuthority('Authorities.Package_UPDATE')")
	@Override
	public Mono<Package> update(Package entity) {
		return this.dao.canBeUpdated(entity.getId())
				.flatMap(e -> e.booleanValue() ? super.update(entity) : Mono.empty())
				.switchIfEmpty(Mono.defer(
						() -> securityMessageResourceService.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
										StringFormatter.format(msg, "User", entity.getId()))))));
	}

	@PreAuthorize("hasAuthority('Authorities.Package_UPDATE')")
	@Override
	public Mono<Package> update(ULong key, Map<String, Object> fields) {
		return this.dao.canBeUpdated(key)
				.flatMap(e -> e.booleanValue() ? super.update(key, fields) : Mono.empty())
				.switchIfEmpty(Mono.defer(
						() -> securityMessageResourceService.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
								.flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
										StringFormatter.format(msg, "User", key))))));
	}

	@Override
	protected Mono<Package> updatableEntity(Package entity) {

		return this.read(entity.getId())
				.flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
						.map(ca -> {
							if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
								existing.setBase(false);

							existing.setCode(entity.getCode());
							existing.setDescription(entity.getDescription());
							existing.setName(entity.getName());

							return existing;
						}));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		Map<String, Object> newFields = new HashMap<>();

		if (fields.containsKey(NAME))
			newFields.put(NAME, fields.get(NAME));
		if (fields.containsKey(DESCRIPTION))
			newFields.put(DESCRIPTION, fields.get(DESCRIPTION));
		if (fields.containsKey(CODE))
			newFields.put(CODE, fields.get(CODE));

		if (!fields.containsKey(BASE))
			return Mono.just(newFields);

		return SecurityContextUtil.getUsersContextAuthentication()
				.map(ca -> {

					if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
						newFields.put(BASE, fields.get(BASE));

					return newFields;
				});
	}

	@PreAuthorize("hasAuthority('Authorities.Package_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {
		return this.read(id)
				.flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
						.flatMap(ca -> {
							if (ca.isSystemClient())
								return super.delete(id);

							return this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
									.getClientId()), existing.getClientId())
									.flatMap(managed -> {

										if (managed.booleanValue())
											return super.delete(id);

										return this.securityMessageResourceService
												.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
												.flatMap(msg -> Mono
														.error(() -> new GenericException(HttpStatus.NOT_FOUND,
																StringFormatter.format(msg, PACKAGE, id))));
									});
						})

				)
				.onErrorResume(
						ex -> ex instanceof DataAccessException || ex instanceof R2dbcDataIntegrityViolationException
								? this.securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN, ex,
										SecurityMessageResourceService.DELETE_PACKAGE_ERROR)
								: Mono.error(ex));
	}

	public Mono<List<ULong>> getRolesFromPackage(ULong packageId) {
		return this.dao.getRolesFromPackage(packageId);
	}

	@PreAuthorize("hasAuthority('Authorities.Package_READ') and hasAuthority('Authorities.Role_READ')")
	public Mono<List<Role>> getRolesFromGivenPackage(ULong packageId) {

		return flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca ->

				ca.isSystemClient() ? Mono.just(true)

						: this.read(packageId)
								.flatMap(packagel -> this.clientService.isBeingManagedBy(
										ULongUtil.valueOf(ca.getLoggedInFromClientId()),
										ULongUtil.valueOf(packagel.getClientId())))
								.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(ca, sysOrManaged) -> this.dao.getRolesFromGivenPackage(packageId)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "PackageService.getRolesFromGivenPackage"))
				.switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
						SecurityMessageResourceService.FETCH_ROLE_ERROR, packageId));

	}

	public Mono<List<ULong>> getPermissionsFromPackage(ULong packageId, List<ULong> roles) {
		return this.dao.getPermissionsFromPackage(packageId, roles);
	}

	public Mono<List<ULong>> omitRolesFromBasePackage(List<ULong> roles) {
		return this.dao.omitRolesFromBasePackage(roles);
	}

	public Mono<List<ULong>> omitPermissionsFromBasePackage(List<ULong> permissions) {
		return this.dao.omitPermissionsFromBasePackage(permissions);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_Package')")
	public Mono<Boolean> assignRoleToPackage(ULong packageId, ULong roleId) {

		return this.dao.checkRoleAssignedForPackage(packageId, roleId)
				.flatMap(result -> {
					if (result.booleanValue())
						return Mono.just(result);

					return flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> this.dao.readById(packageId),

							(ca, packageRecord) -> this.roleService.read(roleId),

							(ca, packageRecord, roleRecord) ->

							ca.isSystemClient() ? Mono.just(true)
									: this.checkRoleAndPackageClientsAreManaged(ULong.valueOf(ca.getUser()
											.getClientId()), packageRecord.getClientId(), roleRecord.getClientId()),

							(ca, packageRecord, roleRecord, rolePackageManaged) ->

							Mono.just(packageRecord.getClientId()
									.equals(roleRecord.getClientId()))
									.flatMap(e -> {
										if (e.booleanValue())
											return Mono.just(e);

										return this.dao.checkRoleAvailableForGivenPackage(packageId, roleId)
												.flatMap(BooleanUtil::safeValueOfWithEmpty);
									}),

							(ca, packageRecord, roleRecord, rolePackageManaged, hasRole) ->

							this.dao.addRoleToPackage(packageId, roleId)
									.map(e -> {
										if (e.booleanValue())
											super.assignLog(packageId, ASSIGNED_ROLE);
										return e;
									})

				).contextWrite(Context.of(LogUtil.METHOD_NAME, "PackageService.assignRoleToPackage"))
							.switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
									SecurityMessageResourceService.ASSIGN_ROLE_ERROR, roleId, packageId));
				});

	}

	private Mono<Boolean> checkRoleAndPackageClientsAreManaged(ULong loggedInClientId, ULong packageClientId,
			ULong roleClientId) {

		return flatMapMono(

				() -> this.clientService.isBeingManagedBy(loggedInClientId, packageClientId)
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				roleManaged -> this.clientService.isBeingManagedBy(loggedInClientId, roleClientId)
						.flatMap(BooleanUtil::safeValueOfWithEmpty)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "PackageService.checkRoleAndPackageClientsAreManaged"));

	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_Package')")
	public Mono<Boolean> removeRoleFromPackage(ULong packageId, ULong roleId) {

		return this.dao.checkRoleAssignedForPackage(packageId, roleId)
				.flatMap(result -> {
					if (!result.booleanValue())
						return securityMessageResourceService.throwMessage(HttpStatus.NOT_FOUND,
								SecurityMessageResourceService.OBJECT_NOT_FOUND, packageId, roleId);

					return flatMapMono(

							SecurityContextUtil::getUsersContextAuthentication,

							ca -> ca.isSystemClient() ? Mono.just(true)

									: flatMapMono(

											() -> this.dao.readById(packageId),

											packageRecord -> this.roleService.read(roleId),

											(packageRecord, roleRecord) -> this.checkRoleAndPackageClientsAreManaged(
													ULong.valueOf(ca.getUser()
															.getClientId()),
													packageRecord.getClientId(), roleRecord.getClientId())

									).contextWrite(
											Context.of(LogUtil.METHOD_NAME, "PackageService.removeRoleFromPackage")),

							(ca, sysOrManaged) -> this.dao.checkRoleFromBasePackage(roleId)
									.flatMap(isBase -> {
										if (isBase.booleanValue())
											return Mono.just(true);

										return this.removeRoleFromUsers(packageId, roleId);
									}),

							(ca, sysOrManaged, removeUsersRole) -> this.removePermissionsFromUsers(packageId, roleId),

							(ca, sysOrManaged, removeUsersRole, removeUsersPermission) ->

							this.dao.removeRole(packageId, roleId)
									.map(removed -> {

										if (removed.booleanValue())
											super.unAssignLog(packageId, UNASSIGNED_ROLE + roleId);

										return removed;
									})

				);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "PackageService.removeRoleFromPackage"))
				.switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
						SecurityMessageResourceService.ROLE_REMOVE_FROM_PACKAGE_ERROR, roleId, packageId));

	}

	private Mono<Boolean> removeRoleFromUsers(ULong packageId, ULong roleId) {

		return flatMapMono(

				() -> this.dao.getUsersListFromPackage(packageId)
						.flatMap(packageUsers -> packageUsers.isEmpty() ? Mono.just(new ArrayList<ULong>())
								: this.dao.getUsersListFromRoleForOtherPackages(packageId, roleId, packageUsers)),

				usersList -> this.dao.removeRoleFromUsers(roleId, usersList)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "PackageService.removeRoleFromUsers"));
	}

	private Mono<Boolean> removePermissionsFromUsers(ULong packageId, ULong roleId) {

		return flatMapMono(

				() -> this.roleService.getPermissionsFromRole(roleId)
						.flatMap(permissions -> permissions.isEmpty() ? Mono.just(new ArrayList<ULong>())
								: this.dao.removePermissionsPartOfBasePackage(permissions)),

				permissionsList -> this.dao.getUsersListFromPackage(packageId)
						.flatMap(users -> users.isEmpty() ? Mono.just(new ArrayList<ULong>())
								: this.dao.removeUsersWithPermissionsFromRoleForOtherPackages(packageId,
										permissionsList, users)),

				(permissionsList, usersList) -> !usersList.isEmpty() && !permissionsList.isEmpty()
						? userService.removeFromPermissionList(usersList, permissionsList)
						: Mono.just(true)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "PackageService.removePermissionsFromUsers"));
	}

}
