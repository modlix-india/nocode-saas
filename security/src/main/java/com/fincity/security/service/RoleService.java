package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.List;
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
import com.fincity.security.dao.RoleDao;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;
import com.fincity.security.util.ULongUtil;

import reactor.core.publisher.Mono;

@Service
public class RoleService extends AbstractSecurityUpdatableDataService<SecurityRoleRecord, ULong, Role, RoleDao> {

	private static final String DESCRIPTION = "description";

	private static final String NAME = "name";

	private static final String ASSIGNED_PERMISSION = " Permission is assigned to Role ";

	private static final String UNASSIGNED_PERMISSION = " Permission is remove from Role ";

	@Autowired
	private ClientService clientService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.ROLE;
	}

	@Override
	@PreAuthorize("hasAuthority('Authorities.Role_CREATE')")
	public Mono<Role> create(Role entity) {
		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{
			        if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				        return super.create(entity);

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

	@PreAuthorize("hasAuthority('Authorities.Role_READ')")
	@Override
	public Mono<Role> read(ULong id) {
		return super.read(id);
	}

	@PreAuthorize("hasAuthority('Authorities.Role_READ')")
	@Override
	public Mono<Page<Role>> readPageFilter(Pageable pageable, AbstractCondition cond) {
		return super.readPageFilter(pageable, cond);

	}

	@PreAuthorize("hasAuthority('Authorities.Role_UPDATE')")
	@Override
	public Mono<Role> update(Role entity) {

		return this.dao.canBeUpdated(entity.getId())
		        .flatMap(e -> e.booleanValue() ? super.update(entity) : Mono.empty())
		        .switchIfEmpty(Mono.defer(
		                () -> securityMessageResourceService.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
		                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
		                                StringFormatter.format(msg, "User", entity.getId()))))));
	}

	@PreAuthorize("hasAuthority('Authorities.Role_UPDATE')")
	@Override
	public Mono<Role> update(ULong key, Map<String, Object> fields) {
		return this.dao.canBeUpdated(key)
		        .flatMap(e -> e.booleanValue() ? super.update(key, fields) : Mono.empty())
		        .switchIfEmpty(Mono.defer(
		                () -> securityMessageResourceService.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
		                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
		                                StringFormatter.format(msg, "User", key))))));
	}

	@Override
	public Mono<Role> updatableEntity(Role entity) {
		return this.read(entity.getId())
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .map(ca ->
						{
			                existing.setName(entity.getName());
			                existing.setDescription(entity.getDescription());
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

		return Mono.just(newFields);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_Role')")
	public Mono<Boolean> assignPermissionToRole(ULong roleId, ULong permissionId) {

		return this.dao.checkPermissionExistsForRole(roleId, permissionId)
		        .flatMap(result ->
				{

			        if (result.booleanValue())
				        return Mono.just(true);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> this.dao.readById(roleId),

			                (ca, roleRecord) -> this.dao.getPermissionRecord(permissionId),

			                (ca, roleRecord, permissionRecord) ->

							clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							        .getClientId()), roleRecord.getClientId())
							        .flatMap(this::checkTruth),

			                (ca, roleRecord, permissionRecord, roleManaged) ->

							clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
							        .getClientId()), permissionRecord.getClientId())
							        .flatMap(this::checkTruth),

			                (ca, roleRecord, permissionRecord, roleManaged, permissionManaged) ->

							Mono.just(roleRecord.getClientId()
							        .equals(permissionRecord.getClientId())),

			                (ca, roleRecord, permissionRecord, roleManaged, permissionManaged, sameClient) ->

							sameClient.booleanValue() ? Mono.just(true)
							        : this.dao.checkPermissionAvailableForGivenRole(roleId, permissionId),

			                // check permission part
			                // of client or is
			                // it base package

			                (ca, roleRecord, permissionRecord, roleManaged, permissionManaged, sameClient,
			                        hasPermission) ->
							{
				                if (hasPermission.booleanValue())
					                return this.dao.addPermission(roleId, permissionId)
					                        .map(e ->
											{
						                        if (e.booleanValue())
							                        super.assignLog(roleId, ASSIGNED_PERMISSION);
						                        return e;
					                        });

				                return Mono.empty();
			                }

				).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				        SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR_FOR_ROLE, permissionId, roleId));

		        });

	}

	public Mono<Set<ULong>> fetchPermissionsFromRole(ULong roleId) {
		return this.dao.fetchPermissionsFromRole(roleId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_Role')")
	public Mono<Boolean> removePermissionFromRole(ULong roleId, ULong permissionId) {

		return this.dao.checkPermissionExistsForRole(roleId, permissionId)
		        .flatMap(result ->
				{
			        if (!result.booleanValue())
				        return Mono.just(true);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> this.dao.readById(roleId),

			                (ca, roleRecord) -> ca.isSystemClient() ? Mono.just(true)
			                        : this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
			                                .getClientId()), roleRecord.getClientId())
			                                .flatMap(this::checkTruth),

			                (ca, roleRecord, sysOrManaged) -> this.dao.removePermissionFromRole(roleId, permissionId),

			                (ca, roleRecord, sysOrManaged, permissionRemoved) -> this.dao
			                        .checkPermissionBelongsToBasePackage(roleId, permissionId),

			                (ca, roleRecord, sysOrManaged, permissionRemoved, isBase) -> isBase.booleanValue() ?

			                        Mono.just(true)
			                        : this.removePermission(roleId, permissionId, roleRecord.getClientId())
			                                .flatMap(this::checkTruth)
			                                .map(e ->
											{
				                                super.assignLog(roleId, UNASSIGNED_PERMISSION + permissionId);
				                                return e;
			                                })

				).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				        SecurityMessageResourceService.REMOVE_PERMISSION_FROM_ROLE_ERROR, permissionId, roleId));

		        });

	}

	public Mono<Boolean> removePermission(ULong roleId, ULong permissionId, ULong roleClientId) {

//		Find all the users of the clients who have the package assigned to this role
//		and users of the role's client id and omit those users who belong to the clients
//		who get this permission from a different role and remove the permission if assigned
//		 to these users directly.

		return flatMapMono(

		        () -> this.dao.getUsersListFromRole(roleId),

		        roleUsers -> this.dao.getUsersListFromClient(roleClientId),

		        (roleUsers, roleClientUsers) -> this.dao.getUsersListFromDifferentRole(roleId, permissionId),

		        (roleUsers, roleClientUsers, differentRoleUsers) ->
				{
			        System.out.println(roleUsers + " roleUsers ");
			        System.out.println(roleClientUsers + " roleClientUsers ");
			        System.out.println(differentRoleUsers + " differentRoleUsers ");

			        roleUsers.addAll(roleClientUsers);
			        roleUsers.removeAll(differentRoleUsers);

			        System.out.println(" after omiting users list " + roleUsers);

			        return Mono.just(roleUsers);
		        },

		        (roleUsers, roleClientUsers, differentRoleUsers, finalUsers) ->
				{
			        System.out.println(" calling from last step of remove permission subroutine");
			        return this.dao.removePemissionFromUsers(permissionId, finalUsers);
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.REMOVE_PERMISSION_FROM_ROLE_ERROR, permissionId, roleId));
	}

	private Mono<Boolean> checkTruth(Boolean b) {
		return b.booleanValue() ? Mono.just(b) : Mono.empty();
	}

	public Mono<List<ULong>> getPermissionsIdFromRole(ULong roleId) {

		return this.dao.getPermissionsFromRole(roleId);
	}
}
