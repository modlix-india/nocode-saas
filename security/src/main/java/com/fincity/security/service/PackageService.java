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

	@PreAuthorize("hasAuthority('Authorities.Package_READ')")
	@Override
	public Mono<Package> read(ULong id) {
		return super.read(id);
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
			newFields.put(NAME, fields.get(NAME));
		if (fields.containsKey(DESCRIPTION))
			newFields.put(DESCRIPTION, fields.get(DESCRIPTION));
		if (fields.containsKey(CODE))
			newFields.put(CODE, fields.get(CODE));

		if (!fields.containsKey(BASE))
			return Mono.just(newFields);

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ca ->
				{

			        if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				        newFields.put(BASE, fields.get(BASE));

			        return newFields;
		        });
	}

	@PreAuthorize("hasAuthority('Authorities.Package_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {
		return super.delete(id);
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

	// improve readability
	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_Package')")
	public Mono<Boolean> assignRoleToPackage(ULong packageId, ULong roleId) {

		return this.dao.checkRoleAssignedForPackage(packageId, roleId)
		        .flatMap(result ->
				{
			        if (result.booleanValue())
				        return Mono.just(result);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> this.dao.readById(packageId),

			                (ca, packageRecord) -> this.roleService.read(roleId),

			                (ca, packageRecord, roleRecord) ->

							ca.isSystemClient() ? Mono.just(true)
							        : flatMapMono(() -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							                .getClientId()), packageRecord.getClientId())
							                .flatMap(this::checkTruth),

							                roleManaged -> this.clientService
							                        .isBeingManagedBy(ULong.valueOf(ca.getUser()
							                                .getClientId()), roleRecord.getClientId())
							                        .flatMap(this::checkTruth)),

			                (ca, packageRecord, roleRecord, rolePackageManaged) ->

							packageRecord.getClientId()
							        .equals(roleRecord.getClientId()) ? Mono.just(true)
							                : this.dao.checkRoleAvailableForGivenPackage(packageId, roleId),

			                // check
			                // role is
			                // part
			                // of
			                // package
			                // which is
			                // assigned
			                // to
			                // client

			                (ca, packageRecord, roleRecord, rolePackageManaged, hasRole) ->
							{

				                if (hasRole.booleanValue())

					                return this.dao.addRoleToPackage(packageId, roleId)
					                        .map(e ->
											{
						                        super.assignLog(packageId, ASSIGNED_ROLE);
						                        return e;
					                        });

				                return Mono.empty();
			                }

				).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				        SecurityMessageResourceService.ASSIGN_ROLE_ERROR, roleId, packageId));
		        });

	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Role_To_Package')")
	public Mono<Boolean> removeRoleFromPackage(ULong packageId, ULong roleId) {

		return this.dao.checkRoleAssignedForPackage(packageId, roleId)
		        .flatMap(result ->
				{
			        if (!result.booleanValue())
				        return Mono.just(true);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> this.dao.readById(packageId),

			                (ca, packageRecord) -> ca.isSystemClient() ? Mono.just(true)
			                        : flatMapMono(

			                                () -> this.roleService.read(roleId),

			                                roleRecord -> this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
			                                        .getClientId()),

			                                        roleRecord.getClientId())
			                                        .flatMap(this::checkTruth),

			                                (roleRecord, roleManaged) -> this.clientService
			                                        .isBeingManagedBy(ULong.valueOf(ca.getUser()
			                                                .getClientId()), packageRecord.getClientId())
			                                        .flatMap(this::checkTruth)),

			                (ca, packageRecord, sysOrManaged) -> this.dao.removeRole(packageId, roleId),

			                (ca, packageRecord, sysOrManaged, roleRemoved) -> this.dao
			                        .checkRoleFromBasePackage(packageId, roleId),

			                (ca, packageRecord, sysOrManaged, roleRemoved,
			                        isBase) -> isBase.booleanValue() ? Mono.just(true) : Mono.just(true)
			        // call a function from here

			        );
		        });
	}

	public Mono<Boolean> removeRole(ULong packageId, ULong roleId) {

		return flatMapMono(

		        () -> this.dao.getUsersListFromPackage(packageId),

		        packageUsers -> this.dao.getUsersListFromPackageForOtherRole(packageId, roleId),

		        (packageUsers, usersFromOtherRole) ->
				{
			        System.out.println("packageUsers " + packageUsers);
			        System.out.println("usersFromOtherRole " + usersFromOtherRole);
			        packageUsers.removeAll(usersFromOtherRole);
			        System.out.println("packageUsers " + packageUsers);
			        return this.dao.removeRoleFromUsers(roleId, packageUsers);
		        },

		        (packageUsers, usersFromOtherRole, rolesRemoved) -> this.roleService.getPermissionsIdFromRole(roleId),

		        (packageUsers, usersFromOtherRole, rolesRemoved, permissionsList) -> this.dao
		                .getPermissionsFromBasePackage(packageId, permissionsList),
		                
		                

		);
	}

	private Mono<Boolean> checkTruth(Boolean b) {
		return b.booleanValue() ? Mono.just(b) : Mono.empty();
	}

}
