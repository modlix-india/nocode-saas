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
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.security.dao.RoleDAO;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import reactor.core.publisher.Mono;

@Service
public class RoleService extends AbstractSecurityUpdatableDataService<SecurityRoleRecord, ULong, Role, RoleDAO> {

	private static final String DESCRIPTION = "description";

	private static final String NAME = "name";

	private static final String ASSIGNED_PERMISSION = " Permission is assigned to Role ";

	private static final String UNASSIGNED_PERMISSION = " Permission is remove from Role ";

	private static final String ROLE = "role";

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

	@PreAuthorize("hasAuthority('Authorities.Role_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {

		return this.read(id)
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{
			                if (ca.isSystemClient())
				                return super.delete(id);

			                return this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                        .getClientId()), existing.getClientId())
			                        .flatMap(managed ->
									{

				                        if (managed.booleanValue())
					                        return super.delete(id);

				                        return this.securityMessageResourceService
				                                .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                                .flatMap(msg -> Mono
				                                        .error(() -> new GenericException(HttpStatus.NOT_FOUND,
				                                                StringFormatter.format(msg, ROLE, id))));
			                        });
		                })

				)
		        .onErrorResume(
		                ex -> ex instanceof DataAccessException || ex instanceof R2dbcDataIntegrityViolationException
		                        ? this.securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN, ex,
		                                SecurityMessageResourceService.DELETE_ROLE_ERROR)
		                        : Mono.error(ex));

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

							ca.isSystemClient() ? Mono.just(true)
							        : this.checkPermissionAndRoleClientsAreManaged(ULong.valueOf(ca.getUser()
							                .getClientId()), roleRecord.getClientId(), permissionRecord.getClientId()),

			                (ca, roleRecord, permissionRecord, sysOrManaged) ->

							Mono.just(roleRecord.getClientId()
							        .equals(permissionRecord.getClientId()))
							        .flatMap(e ->
									{
								        if (e.booleanValue())
									        return Mono.just(e);

								        return this.dao.checkPermissionAvailableForGivenRole(roleId, permissionId)
								                .flatMap(BooleanUtil::safeValueOfWithEmpty);
							        }),

			                (ca, roleRecord, permissionRecord, sysOrManaged, hasPermission) ->

							this.dao.addPermission(roleId, permissionId)

							        .map(e ->
									{
								        if (e.booleanValue())
									        super.assignLog(roleId, ASSIGNED_PERMISSION);
								        return e;
							        })

				).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				        SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR_FOR_ROLE, permissionId, roleId));

		        });

	}

	private Mono<Boolean> checkPermissionAndRoleClientsAreManaged(ULong loggedInClientId, ULong roleClientId,
	        ULong permissionClientId) {

		return flatMapMono(

		        () -> clientService.isBeingManagedBy(loggedInClientId, roleClientId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        roleManaged -> clientService.isBeingManagedBy(loggedInClientId, permissionClientId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty)

		);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_Role')")
	public Mono<Boolean> removePermissionFromRole(ULong roleId, ULong permissionId) {

		return this.dao.checkPermissionExistsForRole(roleId, permissionId)
		        .flatMap(result ->
				{
			        if (!result.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.NOT_FOUND,
				                SecurityMessageResourceService.OBJECT_NOT_FOUND, roleId, permissionId);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> this.dao.readById(roleId),

			                (ca, roleRecord) -> ca.isSystemClient() ? Mono.just(true)
			                        : this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
			                                .getClientId()), roleRecord.getClientId())
			                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

			                (ca, roleRecord, sysOrManaged) -> this.dao
			                        .checkPermissionBelongsToBasePackage(permissionId),

			                (ca, roleRecord, sysOrManaged, fromBase) -> fromBase.booleanValue() ?

			                        Mono.just(true)
			                        : this.removePermissionsFromUsers(roleId, permissionId, roleRecord.getClientId())
			                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

			                (ca, roleRecord, sysOrManaged, fromBase, permissionsRemoved) ->

							this.dao.removePermissionFromRole(roleId, permissionId)
							        .map(e ->
									{
								        if (e.booleanValue())
									        super.assignLog(roleId, UNASSIGNED_PERMISSION + permissionId);

								        return e;
							        })

				).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				        SecurityMessageResourceService.REMOVE_PERMISSION_FROM_ROLE_ERROR, permissionId, roleId));

		        });

	}

	private Mono<Boolean> removePermissionsFromUsers(ULong roleId, ULong permissionId, ULong roleClientId) {

		return flatMapMono(

		        () -> this.dao.getUsersListFromRole(roleId),

		        roleUsers -> this.dao.getUsersListFromClient(roleClientId),

		        (roleUsers, roleClientUsers) ->
				{

			        if (roleUsers.isEmpty())
				        return Mono.just(new ArrayList<>());

			        else if (roleClientUsers.isEmpty())
				        return this.dao.omitUsersListFromDifferentRole(roleId, permissionId, roleUsers);

			        roleClientUsers.stream()
			                .forEach(roleUsers::remove);

			        return this.dao.omitUsersListFromDifferentRole(roleId, permissionId, roleUsers);
		        },

		        (roleUsers, roleClientUsers, finalRoleUsers) -> finalRoleUsers.isEmpty() ? Mono.just(true)
		                : this.dao.removePemissionFromUsers(permissionId, finalRoleUsers)

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.REMOVE_PERMISSION_FROM_ROLE_ERROR, permissionId, roleId));
	}

	public Mono<List<ULong>> getPermissionsFromRole(ULong roleId) {

		return this.dao.getPermissionsFromRole(roleId);
	}

	public Mono<List<Permission>> getPermissionsFromGivenRole(ULong roleId) {

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->

				Mono.just(SecurityContextUtil.hasAuthority("Authorities.Permission_READ", ca.getAuthorities())
				        && SecurityContextUtil.hasAuthority("Authorities.Role_READ", ca.getAuthorities()))
				        .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, hasAuthorities) ->

				ca.isSystemClient() ? Mono.just(true)
				        : this.read(roleId)
				                .flatMap(role -> this.clientService.isBeingManagedBy(
				                        ULongUtil.valueOf(ca.getLoggedInFromClientId()),
				                        ULongUtil.valueOf(role.getClientId())))
				                .flatMap(BooleanUtil::safeValueOfWithEmpty)

		        ,

		        (ca, hasAuthorities, sysOrManaged) -> this.dao.getPermissionsFromGivenRole(roleId)

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.FETCH_PERMISSION_ERROR, roleId));

	}
}
