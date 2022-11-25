package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.service.CacheService;
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
	private static final String CACHE_NAME_CLIENT_URL = "clientClientURL";
	private static final String CACHE_NAME_CLIENT_CODE = "clientCodeId";

	private static final String CACHE_CLIENT_URI = "uri";

	@Autowired
	private CacheService cacheService;

	@Autowired
	private ClientUrlService clientUrlService;

	private PackageService packageService;

	private UserService userService;

	@Autowired
	private SecurityMessageResourceService securityMessageResourceService;

	public void setUserService(UserService userService) {
		this.userService = userService;
	}

	public void setPackageService(PackageService packageService) {
		this.packageService = packageService;
	}

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
		        .flatMap(id -> this.dao.readById(id));
	}

	public Mono<ClientUrlPattern> getClientPattern(String uriScheme, String uriHost, String uriPort) {

		Mono<String> key = cacheService.makeKey(CACHE_CLIENT_URI, uriScheme, uriHost, ":", uriPort);

		Mono<ClientUrlPattern> clientId = key.flatMap(e -> cacheService.get(CACHE_NAME_CLIENT_URL, e));

		String finScheme = uriScheme;
		String finHost = uriHost;
		Integer finPort = Integer.valueOf(uriPort);

		return clientId.switchIfEmpty(Mono.defer(() -> clientUrlService.readAllAsClientURLPattern()
		        .flatMapIterable(e -> e)
		        .filter(e -> e.isValidClientURLPattern(finScheme, finHost, finPort))
		        .take(1)
		        .collectList()
		        .flatMap(e -> e.isEmpty() ? Mono.empty() : Mono.just(e.get(0)))
		        .flatMap(e -> key.flatMap(k -> cacheService.put(CACHE_NAME_CLIENT_URL, e, k)
		                .map(ClientUrlPattern.class::cast)))));
	}

	public Mono<Set<ULong>> getPotentialClientList(ServerHttpRequest request) {

		return this.getClientBy(request)
		        .map(Client::getId)
		        .flatMap(this::getPotentialClientList);
	}

	public Mono<Set<ULong>> getPotentialClientList(ULong k) {

		Mono<Set<ULong>> clientList = cacheService.get(CACHE_NAME_CLIENT_RELATION, k);

		return clientList.switchIfEmpty(Mono.defer(() -> this.dao.findManagedClientList(k)
		        .flatMap(v -> cacheService.put(CACHE_NAME_CLIENT_RELATION, v, k))));
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {
		return this.dao.isBeingManagedBy(managingClientId, clientId);
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong clientId) {

		Mono<ClientPasswordPolicy> policy = cacheService.get(CACHE_NAME_CLIENT_PWD_POLICY, clientId);

		return policy.switchIfEmpty(Mono.defer(() -> this.dao.getClientPasswordPolicy(clientId)
		        .switchIfEmpty(Mono.just(new ClientPasswordPolicy()))
		        .flatMap(v -> cacheService.put(CACHE_NAME_CLIENT_PWD_POLICY, v, clientId))));
	}

	public Mono<Tuple2<String, String>> getClientTypeNCode(ULong id) {

		Mono<Tuple2<String, String>> clientType = cacheService.get(CACHE_NAME_CLIENT_TYPE, id);

		return clientType.switchIfEmpty(Mono.defer(() -> this.dao.getClientTypeNCode(id)
		        .flatMap(v -> cacheService.put(CACHE_NAME_CLIENT_TYPE, v, id))));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
	@Override
	public Mono<Client> create(Client entity) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca -> super.create(entity).map(e ->
				{
			        if (!ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())) {
				        SecurityContextUtil.getUsersContextUser()
				                .map(ContextUser::getClientId)
				                .map(manageClientId -> this.addManageRecord(ULong.valueOf(manageClientId), e.getId()))
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

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> packageService.isBasePackage(packageId),

		        (ca, basePackage) -> this.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                .getClientId()), clientId),

		        (ca, basePackage, isManaged) -> isManaged.booleanValue()
		                ? this.packageService.getClientIdFromPackage(packageId)
		                : Mono.empty(),

		        (ca, basePackage, isManaged, clientIdFromPackage) -> isManaged.booleanValue()
		                ? this.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                        .getClientId()), clientIdFromPackage)
		                : Mono.empty(),

		        (ca, basePackage, isManaged, clientIdFromPackage, clientApplicable) ->
				{
			        if ((!basePackage.booleanValue() && clientApplicable.booleanValue())
			                && (ca.isSystemClient() || isManaged.booleanValue())) {

				        return this.dao.addPackageToClient(clientId, packageId);

			        }

			        return Mono.empty();
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.ASSIGN_PACKAGE_ERROR, packageId, clientId));

	}

	public Mono<Boolean> checkPermissionAvailableForGivenClient(ULong clientId, ULong permissionId) {
		return this.dao.checkPermissionAvailableForGivenClient(clientId, permissionId);
	}

	public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {
		return this.dao.isBeingManagedBy(managingClientCode, clientCode);
	}
	
	public Mono<Boolean> isUserBeingManaged(ULong userId, String clientCode) {

		return this.dao.isUserBeingManaged(userId, clientCode);
	}

	public Mono<Client> getClientBy(String clientCode) {

		return cacheService.get(CACHE_NAME_CLIENT_CODE, clientCode)
		        .map(Client.class::cast)
		        .switchIfEmpty(Mono.defer(() -> this.dao.getClientBy(clientCode)
		                .map(client ->
						{
			                cacheService.put(CACHE_NAME_CLIENT_CODE, client, clientCode);
			                return client;
		                })));
	}

	public Mono<Boolean> checkClientApplicableForGivenPackage(ULong packageId, ULong clientId) {
		return this.dao.checkClientApplicableForGivenPackage(packageId, clientId);
	}

	public Mono<Boolean> checkPackageApplicableForGivenClient(ULong cliendId, ULong packageId) {
		return this.dao.checkPackageApplicableForGivenClient(cliendId, packageId);
	}

	public Mono<Boolean> checkRoleApplicableForSelectedClient(ULong clientId, ULong roleId) {
		return this.dao.checkRoleApplicableForSelectedClient(clientId, roleId);
	}

	@PreAuthorize("hasAuthority('Authorities.ASSIGN_Package_To_Client')")
	public Mono<Boolean> removePackageFromClient(ULong clientId, ULong packageId) {

		Mono<Set<ULong>> rolesFromFMM = flatMapMono(SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.packageService.read(packageId),

		        (ca, packageRecord) -> this.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                .getClientId()), packageRecord.getClientId()),

		        (ca, packageRecord, isManaged) ->
				{

			        if (ca.isSystemClient() || isManaged.booleanValue())
				        return Mono.just(true);

			        return Mono.empty();
		        },

		        (ca, packageRecord, isManaged, sysOrManaged) -> this.dao.removePackage(clientId, packageId),

		        (ca, packageRecord, isManaged, sysOrManaged, removed) -> this.packageService
		                .getRolesFromPackage(packageId),

		        (ca, packageRecord, isManaged, sysOrManaged, removed, roles) -> this.packageService
		                .getRolesAfterOmittingFromBasePackage(roles)

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.REMOVE_PACKAGE_ERR0R, packageId, clientId));

		return flatMapMono(

		        () -> this.dao.getClientListFromPackage(packageId),

		        clientList -> this.dao.getUsersListFromClient(clientList),

		        (clientList, userList) ->
				{
			        if (userList == null || rolesFromFMM == null)
				        return Mono.empty();

			        // remove roles from users

			        rolesFromFMM.subscribe(roles -> roles.forEach(
			                role -> userList.forEach(user -> this.userService.removeRoleFromUser(user, role))));

			        return Mono.just(true);
		        },

		        (clientList, userList, userAfterRemoval) ->

				this.packageService.getPermissionsFromPackage(packageId),

		        (clientList, userList, userAfterRemoval, permissionList) ->

				this.packageService.omitPermissionsFromBasePackage(permissionList),

		        (clientList, userList, userAfterRemoval, permissionList, finalPermissions) ->
				{
			        if (finalPermissions == null || finalPermissions.isEmpty() || userList == null)
				        return Mono.empty();

			        // remove permissions from user

			        finalPermissions.forEach(permission -> userList
			                .forEach(user -> this.userService.removePermissionFromUser(user, permission)));

			        return Mono.just(true);
		        }

		).switchIfEmpty(securityMessageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		        SecurityMessageResourceService.REMOVE_PACKAGE_ERR0R, packageId, clientId));
	}
}
