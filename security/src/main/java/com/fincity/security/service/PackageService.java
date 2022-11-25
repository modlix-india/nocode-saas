package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.PackageDAO;
import com.fincity.security.dto.Package;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityPackageRecord;
import com.fincity.security.util.ULongUtil;

import reactor.core.publisher.Mono;

@Service
public class PackageService extends
        AbstractSecurityUpdatableDataService<SecurityPackageRecord, ULong, com.fincity.security.dto.Package, PackageDAO> {

	private static final String BASE = "base";

	private static final String CODE = "code";

	private static final String DESCRIPTION = "description";

	private static final String NAME = "name";

	private ClientService clientService;

	@Autowired
	private RoleService roleService;

	@Autowired
	private UserService userService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	public void setClientService(ClientService clientService) {
		this.clientService = clientService;
	}

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.PACKAGE;
	}

	@PreAuthorize("hasPermission('Authorities.Package_CREATE')")
	@Override
	public Mono<Package> create(Package entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

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
			                .flatMap(managed ->
							{
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

	@PreAuthorize("hasPermission('Authorities.Package_READ')")
	@Override
	public Mono<Package> read(ULong id) {
		return super.read(id);
	}

	@PreAuthorize("hasPermission('Authorities.Package_READ')")
	@Override
	public Mono<Page<Package>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasPermission('Authorities.Package_UPDATE')")
	@Override
	public Mono<Package> update(Package entity) {
		return this.dao.canBeUpdated(entity.getId())
		        .flatMap(e -> e.booleanValue() ? super.update(entity) : Mono.empty())
		        .switchIfEmpty(Mono.defer(
		                () -> securityMessageResourceService.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
		                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
		                                StringFormatter.format(msg, "User", entity.getId()))))));
	}

	@PreAuthorize("hasPermission('Authorities.Package_UPDATE')")
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
		                .map(ca ->
						{
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
			newFields.put(NAME, fields.containsKey(NAME));
		if (fields.containsKey(DESCRIPTION))
			newFields.put(DESCRIPTION, fields.containsKey(DESCRIPTION));
		if (fields.containsKey(CODE))
			newFields.put(CODE, fields.containsKey(CODE));

		if (!fields.containsKey(BASE))
			return Mono.just(newFields);

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ca ->
				{

			        if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				        newFields.put(BASE, fields.containsKey(BASE));

			        return newFields;
		        });
	}

	protected Mono<Boolean> isBasePackage(ULong packageId) {
		return this.dao.readById(packageId)
		        .map(Package::isBase);
	}

	public Mono<ULong> getClientIdFromPackage(ULong packageId) {
		return this.dao.getClientIdFromPackage(packageId);
	}

	public Mono<Set<ULong>> getRolesFromPackage(ULong packageId) {
		return this.dao.getRolesFromPackage(packageId);
	}

	public Mono<Set<ULong>> getRolesAfterOmittingFromBasePackage(Set<ULong> roles) {
		return this.dao.getRolesAfterOmittingFromBasePackage(roles);
	}

	public Mono<Set<ULong>> getPermissionsFromPackage(ULong packageId) {
		return this.dao.getPermissionsFromPackage(packageId);
	}

	public Mono<Set<ULong>> omitPermissionsFromBasePackage(Set<ULong> permissions) {
		return this.dao.omitPermissionsFromBasePackage(permissions);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Role')")
	public Mono<Boolean> removeRoleFromPackage(ULong packageId, ULong roleId) {

		Mono<Boolean> isSystemOrManaged = flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.readById(packageId),

		        (ca, packageRecord) -> this.roleService.getClientIdFromRole(roleId),

		        (ca, packageRecord, clientIdFromRole) -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                .getClientId()), packageRecord.getClientId()),

		        (ca, packageRecord, clientIdFromRole,
		                isPackageManaged) -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                        .getClientId()), clientIdFromRole),

		        (ca, packageRecord, clientIdFromRole, isPackageManaged, isRoleManaged) ->
				{

			        if (ca.isSystemClient() || (isPackageManaged.booleanValue() && isRoleManaged.booleanValue()))
				        return Mono.just(true);

			        return Mono.empty();
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ROLE_REMOVE_FROM_PACKAGE_ERROR, roleId, packageId));

		Mono<Boolean> isBasePackage = flatMapMono(

		        () -> isSystemOrManaged,

		        sysOrManaged -> sysOrManaged.booleanValue() ? this.dao.removeRole(packageId, roleId) : Mono.empty(),

		        (sysOrManaged, roleRemoved) -> this.dao.checkRoleFromBasePackage(roleId)

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ROLE_REMOVE_FROM_PACKAGE_ERROR, roleId, packageId));

		Mono<Set<ULong>> requiredPermissionList = flatMapMono(

		        () -> isBasePackage,

		        isBase -> this.dao.getClientListFromPackage(packageId, roleId),

		        (isBase, clientList) -> this.dao.getFilteredClientListFromDifferentPackage(packageId, roleId,
		                clientList),

		        (isBase, clientList, filteredClientList) -> this.userService.getUserListFromClients(filteredClientList),

		        (isBase, clientList, filteredClientList, users) ->
				{
			        if (users == null) {
				        return Mono.empty();
			        }

			        users.stream()
			                .forEach(userId -> this.userService.removeRoleFromUser(userId, roleId));

			        return Mono.just(true);
		        },

		        (isBase, clientList, filteredClientList, users, roleRemoved) -> // fetch permissions from role

				this.roleService.fetchPermissionsFromRole(roleId),

		        (isBase, clientList, filteredClientList, users, roleRemoved, permissionList) ->

				// omit permission from base package
				this.dao.omitPermissionFromBasePackage(roleId, permissionList)

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ROLE_REMOVE_FROM_PACKAGE_ERROR, roleId, packageId));

		return flatMapMono(

		        () -> requiredPermissionList,

		        permissionList -> // get permissions from given package

				this.dao.fetchClientsFromGivenPackage(packageId),

		        (permissionList, clients) -> // omit users from different package

				this.dao.omitClientsFromDifferentPackage(packageId, clients, permissionList),

		        (permissionList, clients, filteredClientList) -> // get users from clients

				this.userService.getUserListFromClients(filteredClientList),

		        (permissionList, clients, filteredClientList, finalUsers) -> // delete permissions from users
				{
			        if (permissionList == null || finalUsers == null)
				        return Mono.empty();

			        permissionList.stream()
			                .forEach(permissionId -> finalUsers.stream()
			                        .forEach(
			                                userId -> this.userService.removePermissionFromUser(userId, permissionId)));

			        return Mono.just(true);
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ROLE_REMOVE_FROM_PACKAGE_ERROR, roleId, packageId));

	}
}
