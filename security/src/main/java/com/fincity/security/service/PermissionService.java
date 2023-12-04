package com.fincity.security.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.exception.DataAccessException;
import org.jooq.types.ULong;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.PermissionDAO;
import com.fincity.security.dto.Permission;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;
import com.fincity.security.model.TransportPOJO.AppTransportPermission;
import com.fincity.security.model.TransportPOJO.AppTransportRole;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class PermissionService
		extends AbstractSecurityUpdatableDataService<SecurityPermissionRecord, ULong, Permission, PermissionDAO> {

	private static final String PERMISSION = "Permission";

	private ClientService clientService;
	private SecurityMessageResourceService messageResourceService;

	public PermissionService(ClientService clientService,
			SecurityMessageResourceService messageResourceService) {

		this.clientService = clientService;
		this.messageResourceService = messageResourceService;
	}

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.PERMISSION;
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_CREATE')")
	@Override
	public Mono<Permission> create(Permission entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

					if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())) {
						return messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.FORBIDDEN_CREATE, PERMISSION);
					}
					return super.create(entity);
				});
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_READ')")
	@Override
	public Mono<Permission> read(ULong id) {

		return super.read(id).flatMap(p -> SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

					if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
						return Mono.just(p);

					return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
							.getClientId()), p.getClientId())
							.flatMap(managed -> {
								if (managed.booleanValue())
									return Mono.just(p);

								return messageResourceService.throwMessage(
										msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
										SecurityMessageResourceService.OBJECT_NOT_FOUND, PERMISSION, id);
							});

				}));
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_READ')")
	@Override
	public Mono<Page<Permission>> readPageFilter(Pageable pageable, AbstractCondition condition) {

		return this.dao.readPageFilter(pageable, condition);
	}

	@Override
	protected Mono<Permission> updatableEntity(Permission entity) {

		return this.read(entity.getId())
				.flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
						.flatMap(ca -> {

							existing.setDescription(entity.getDescription());
							existing.setName(entity.getName());

							if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
								return Mono.just(existing);

							return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
									.getClientId()), existing.getClientId())
									.flatMap(managed -> {
										if (managed.booleanValue())
											return Mono.just(existing);

										return messageResourceService
												.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
												.flatMap(msg -> Mono.error(() -> new GenericException(
														HttpStatus.NOT_FOUND,
														StringFormatter.format(msg, PERMISSION, entity.getId()))));
									});

						}));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return this.read(key)
				.flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
						.flatMap(ca -> {

							Map<String, Object> newMap = new HashMap<>();
							newMap.put("description", fields.get("description"));
							newMap.put("name", fields.get("name"));

							if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
								return Mono.just(newMap);

							return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
									.getClientId()), existing.getClientId())
									.flatMap(managed -> {
										if (managed.booleanValue())
											return Mono.just(newMap);

										return messageResourceService
												.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
												.flatMap(msg -> Mono
														.error(() -> new GenericException(HttpStatus.NOT_FOUND,
																StringFormatter.format(msg, PERMISSION, key))));
									});

						}));
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_UPDATE')")
	@Override
	public Mono<Permission> update(Permission entity) {
		return super.update(entity);
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_UPDATE')")
	@Override
	public Mono<Permission> update(ULong key, Map<String, Object> fields) {
		return super.update(key, fields);
	}

	@PreAuthorize("hasAuthority('Authorities.Permission_DELETE')")
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

										return this.messageResourceService
												.getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
												.flatMap(msg -> Mono
														.error(() -> new GenericException(HttpStatus.NOT_FOUND,
																StringFormatter.format(msg, PERMISSION, id))));
									});

						}))
				.onErrorResume(
						ex -> ex instanceof DataAccessException || ex instanceof R2dbcDataIntegrityViolationException
								? this.messageResourceService.throwMessage(
										msg -> new GenericException(HttpStatus.FORBIDDEN, msg, ex),
										SecurityMessageResourceService.DELETE_PERMISSION_ERROR)
								: Mono.error(ex));

	}

	public Mono<List<Permission>> createPermissionsFromTransport(ULong appId, List<AppTransportRole> tRoles,
			List<Role> roles) {

		Map<String, Role> roleIndex = roles.stream().collect(Collectors.toMap(Role::getName, Function.identity()));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> SecurityContextUtil.hasAuthority("", ca.getAuthorities()) ? Mono.just(true)
						: this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.FORBIDDEN_CREATE, PERMISSION),

				(ca, hasAccess) -> this.dao.getPermissionsByNamesAndAppId(
						tRoles.stream().map(AppTransportRole::getPermissions).flatMap(List::stream)
								.map(AppTransportPermission::getPermissionName).toList(),
						appId),

				(ca, hasAccess, existingPerimssions) -> {

					Map<String, Permission> permissionIndex = existingPerimssions.stream()
							.collect(Collectors.toMap(Permission::getName, Function.identity()));

					List<AppTransportPermission> permissions = tRoles.stream().map(AppTransportRole::getPermissions)
							.flatMap(List::stream).filter(e -> !permissionIndex.containsKey(e.getPermissionName()))
							.toList();

					return Flux.fromIterable(permissions).flatMap(p -> {

						Permission permission = new Permission();
						permission.setAppId(appId);
						permission.setName(p.getPermissionName());
						permission.setDescription(p.getPermissionDescription());

						return this.dao.create(permission);
					}).collectList();
				},

				(ca, hasAccess, existingPermissions, perms) -> {

					List<Permission> permissions = new ArrayList<>(existingPermissions);
					permissions.addAll(perms);

					Map<String, Permission> permissionIndex = permissions.stream()
							.collect(Collectors.toMap(Permission::getName, Function.identity()));

					return this.dao.createRolePermissions(tRoles.stream().flatMap(t -> {

						Role role = roleIndex.get(t.getRoleName());

						if (role == null)
							return Stream.of();

						return t.getPermissions().stream()
								.map(p -> Tuples.of(role.getId(), permissionIndex.get(p.getPermissionName()).getId()));

					}).collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2))).map(e -> permissions);
				});
	}

}
