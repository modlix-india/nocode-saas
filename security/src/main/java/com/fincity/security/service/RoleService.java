package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.Map;

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
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dao.RoleDao;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityRoleRecord;
import com.fincity.security.util.ULongUtil;

import reactor.core.publisher.Mono;

@Service
public class RoleService extends AbstractJOOQUpdatableDataService<SecurityRoleRecord, ULong, Role, RoleDao> {

	private static final String DESCRIPTION = "description";

	private static final String NAME = "name";

	@Autowired
	private ClientService clientService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Override
	@PreAuthorize("hasPermission('Authorities.Role_CREATE')")
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

	@PreAuthorize("hasPermission('Authorities.Role_READ')")
	@Override
	public Mono<Role> read(ULong id) {
		return super.read(id);
	}

	@PreAuthorize("hasPermission('Authorities.Role_READ')")
	@Override
	public Mono<Page<Role>> readPageFilter(Pageable pageable, AbstractCondition cond) {
		return super.readPageFilter(pageable, cond);
	}

	@PreAuthorize("hasPermission('Authorities.Role_UPDATE')")
	@Override
	public Mono<Role> update(Role entity) {

		return this.dao.canBeUpdated(entity.getId())
		        .flatMap(e -> e.booleanValue() ? super.update(entity) : Mono.empty())
		        .switchIfEmpty(Mono.defer(
		                () -> securityMessageResourceService.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
		                        .flatMap(msg -> Mono.error(new GenericException(HttpStatus.NOT_FOUND,
		                                StringFormatter.format(msg, "User", entity.getId()))))));
	}

	@PreAuthorize("hasPermission('Authorities.Role_UPDATE')")
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
			newFields.put(NAME, fields.containsKey(NAME));
		if (fields.containsKey(DESCRIPTION))
			newFields.put(DESCRIPTION, fields.containsKey(DESCRIPTION));

		return Mono.just(newFields);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Permission_To_Role')")
	public Mono<Boolean> assignPermissionToRole(ULong roleId, ULong permissionId) {

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.dao.getClientIdFromRole(roleId),

		        (ca, roleClientId) -> this.dao.getClientIdFromPermission(permissionId),

		        (ca, roleClientId, permissionClientId) -> clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                .getClientId()), roleClientId),

		        (ca, roleClientId, permissionClientId,
		                clientManaged) -> clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                        .getClientId()), permissionClientId),

		        (ca, roleClientId, permissionClientId, roleManaged, permissionManaged) -> Mono
		                .just(roleManaged.booleanValue() && permissionManaged.booleanValue()),

		        (ca, roleClientId, permissionClientId, roleManaged, permissionManaged, managesBoth) ->

				this.dao.checkPermissionAvailableForGivenRole(roleId, permissionId),

		        (ca, roleClientId, permissionClientId, roleManaged, permissionManaged, managesBoth,
		                permissionAvailable) ->

				{

			        if ((ca.isSystemClient() || managesBoth.booleanValue())
			                && (roleClientId.equals(permissionClientId) || permissionAvailable.booleanValue()))

				        return this.dao.addPermission(roleId, permissionId)
				                .map(val -> val > 0);

			        return Mono.empty();

		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ASSIGN_PERMISSION_ERROR_FOR_ROLE, permissionId, roleId));
	}

}
