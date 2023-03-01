package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public class ClientService
        extends AbstractSecurityUpdatableDataService<SecurityClientRecord, ULong, Client, ClientDAO> {

	private static final String CACHE_NAME_CLIENT_RELATION = "clientRelation";
	private static final String CACHE_NAME_CLIENT_PWD_POLICY = "clientPasswordPolicy";
	private static final String CACHE_NAME_CLIENT_TYPE = "clientType";
	private static final String CACHE_NAME_CLIENT_CODE = "clientCodeId";
	private static final String CACHE_NAME_CLIENT_URI = "uri";

	private static final String ASSIGNED_PACKAGE = "Package is assigned to Client ";

	private static final String UNASSIGNED_PACKAGE = "Package is removed from Client ";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private ClientUrlService clientUrlService;

	@Autowired
	@Lazy
	private PackageService packageService;

	@Autowired
	@Lazy
	private UserService userService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	@Override
	public SecuritySoxLogObjectName getSoxObjectName() {
		return SecuritySoxLogObjectName.CLIENT;
	}

	public Mono<Client> getClientBy(ServerHttpRequest request) {

		HttpHeaders header = request.getHeaders();

		String uriScheme = header.getFirst("X-Forwarded-Proto");
		String uriHost = header.getFirst("X-Forwarded-Host");
		String uriPort = header.getFirst("X-Forwarded-Port");

		final URI uri1 = request.getURI();

		if (uriScheme == null)
			uriScheme = uri1.getScheme();
		if (uriHost == null)
			uriHost = uri1.getHost();
		if (uriPort == null)
			uriPort = "" + uri1.getPort();

		int ind = uriHost.indexOf(':');
		if (ind != -1)
			uriHost = uriHost.substring(0, ind);

		return getClientPattern(uriScheme, uriHost, uriPort).map(ClientUrlPattern::getIdentifier)
		        .map(ULong::valueOf)
		        .defaultIfEmpty(ULong.valueOf(1l))
		        .flatMap(id -> this.dao.readInternal(id));
	}

	public Mono<ClientUrlPattern> getClientPattern(String uriScheme, String uriHost, String uriPort) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_URI, () -> {

			String finHost = uriHost;

			return clientUrlService.readAllAsClientURLPattern()
			        .flatMapIterable(e -> e)
			        .filter(e -> e.isValidClientURLPattern(finHost, uriPort))
			        .next();

		}, uriScheme, uriHost, ":", uriPort);
	}

	public Mono<Set<ULong>> getPotentialClientList(ServerHttpRequest request) {

		return this.getClientBy(request)
		        .map(Client::getId)
		        .flatMap(this::getPotentialClientList);
	}

	public Mono<Set<ULong>> getPotentialClientList(ULong id) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_RELATION, () -> this.dao.findManagedClientList(id), id);
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {
		return this.dao.isBeingManagedBy(managingClientId, clientId);
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong id) {

		return cacheService.cacheEmptyValueOrGet(CACHE_NAME_CLIENT_PWD_POLICY,
		        () -> this.dao.getClientPasswordPolicy(id), id);
	}

	public Mono<Tuple2<String, String>> getClientTypeNCode(ULong id) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_TYPE, () -> this.dao.getClientTypeNCode(id), id);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
	@Override
	public Mono<Client> create(Client entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca -> super.create(entity).map(e ->
				{

			        if (!ca.isSystemClient()) {

				        ULong mClientId = ULongUtil.valueOf(ca.getUser()
				                .getClientId());
				        this.addManageRecord(mClientId, e.getId())
				                .flatMap(cacheService.evictFunction(CACHE_NAME_CLIENT_RELATION, e.getId()))
				                .subscribe();
			        }

			        return e;
		        }));
	}

	private Mono<Integer> addManageRecord(ULong manageClientId, ULong id) {

		return this.dao.addManageRecord(manageClientId, id);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_READ')")
	@Override
	public Mono<Client> read(ULong id) {
		return super.read(id);
	}

	public Mono<Client> readInternal(ULong id) {
		return this.dao.readInternal(id);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_READ')")
	@Override
	public Mono<Page<Client>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Client> update(Client entity) {
		return super.update(entity);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
	@Override
	public Mono<Client> update(ULong key, Map<String, Object> fields) {
		return super.update(key, fields);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {
		return this.read(id)
		        .map(e ->
				{
			        e.setStatusCode(SecurityClientStatusCode.DELETED);
			        return e;
		        })
		        .flatMap(this::update)
		        .map(e -> 1);
	}

	@Override
	protected Mono<Client> updatableEntity(Client entity) {
		return Mono.just(entity);
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return Mono.just(fields);
	}

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser()
		        .map(ContextUser::getId)
		        .map(ULong::valueOf);
	}

	// For creating user.
	public Mono<Boolean> validatePasswordPolicy(ULong clientId, String password) {

		return this.dao.getClientPasswordPolicy(clientId)
		        .map(e ->
				{
			        // Need to check the password policy
			        return true;
		        })
		        .switchIfEmpty(Mono.just(Boolean.TRUE));
	}

	// For existing user.
	public Mono<Boolean> validatePasswordPolicy(ULong clientId, ULong userId, String password) {

		return this.dao.getClientPasswordPolicy(clientId)
		        .map(e ->
				{
			        // Need to check the password policy
			        return true;
		        })
		        .switchIfEmpty(Mono.just(Boolean.TRUE));
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> assignPackageToClient(ULong clientId, ULong packageId) {

		return this.dao.checkPackageAssignedForClient(clientId, packageId)
		        .flatMap(result ->
				{
			        if (result.booleanValue())
				        return Mono.just(true);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> this.dao.getPackage(packageId),

			                (ca, packageRecord) -> BooleanUtil.safeValueOfWithEmpty(packageRecord.isBase()),

			                (ca, packageRecord, basePackage) ->

							ca.isSystemClient() ? Mono.just(true)
							        : checkClientAndPackageManaged(ULong.valueOf(ca.getUser()
							                .getClientId()), clientId, packageRecord.getClientId()),

			                (ca, packageRecord, basePackage, isManaged) ->

							this.dao.addPackageToClient(clientId, packageId)
							        .map(e ->
									{
								        if (e.booleanValue())
									        super.assignLog(clientId, ASSIGNED_PACKAGE + packageId);

								        return e;
							        })

				).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				        SecurityMessageResourceService.ASSIGN_PACKAGE_ERROR, packageId, clientId));

		        }

				);

	}

	private Mono<Boolean> checkClientAndPackageManaged(ULong loggedInClientId, ULong clientId, ULong packageClientId) {

		return flatMapMono(

		        () -> this.isBeingManagedBy(loggedInClientId, clientId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        clientManaged -> this.isBeingManagedBy(loggedInClientId, packageClientId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty));
	}

	public Mono<Boolean> checkPermissionExistsOrCreatedForClient(ULong clientId, ULong permissionId) {
		return this.dao.checkPermissionExistsOrCreatedForClient(clientId, permissionId);
	}

	public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {
		return this.dao.isBeingManagedBy(managingClientCode, clientCode);
	}

	public Mono<Boolean> isUserBeingManaged(ULong userId, String clientCode) {

		return this.dao.isUserBeingManaged(userId, clientCode);
	}

	public Mono<Client> getClientBy(String clientCode) {

		return cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_CODE, () -> this.dao.getClientBy(clientCode), clientCode);
	}

	public Mono<Boolean> checkRoleExistsOrCreatedForClient(ULong clientId, ULong roleId) {
		return this.dao.checkRoleExistsOrCreatedForClient(clientId, roleId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> removePackageFromClient(ULong clientId, ULong packageId) {

		return this.dao.checkPackageAssignedForClient(clientId, packageId)
		        .flatMap(result ->
				{
			        if (!result.booleanValue())
				        return securityMessageResourceService.throwMessage(HttpStatus.NOT_FOUND,
				                SecurityMessageResourceService.OBJECT_NOT_FOUND, clientId, packageId);

			        return flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> ca.isSystemClient() ? Mono.just(true)
			                        : this.isBeingManagedBy(ULong.valueOf(ca.getUser()
			                                .getClientId()), clientId)
			                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

			                (ca, sysOrManaged) -> this.packageService.getRolesFromPackage(packageId),

			                (ca, sysOrManaged, roles) -> this.packageService.omitRolesFromBasePackage(roles),

			                (ca, sysOrManaged, roles, finalRoles) -> this.dao.findAndRemoveRolesFromUsers(finalRoles,
			                        packageId),

			                (ca, sysOrManaged, roles, finalRoles, rolesRemoved) -> this.packageService
			                        .getPermissionsFromPackage(packageId, finalRoles),

			                (ca, sysOrManaged, roles, finalRoles, rolesRemoved, permissions) -> this.packageService
			                        .omitPermissionsFromBasePackage(permissions),

			                (ca, sysOrManaged, roles, finalRoles, rolesRemoved, permissions,
			                        finalPermissions) -> this.dao.findAndRemovePermissionsFromUsers(finalPermissions,
			                                packageId),

			                (ca, sysOrManaged, roles, finalRoles, rolesRemoved, permissions, finalPermissions,
			                        rolesPermissionsRemoved) ->

							this.dao.removePackage(clientId, packageId)
							        .map(e ->
									{
								        if (e.booleanValue())
									        super.unAssignLog(packageId, UNASSIGNED_PACKAGE + clientId);

								        return e;
							        })

				);
		        })
		        .switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.REMOVE_PACKAGE_ERR0R, packageId, clientId));

	}

}
