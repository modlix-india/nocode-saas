package com.fincity.security.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Package;
import com.fincity.security.dto.Role;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AppService extends AbstractJOOQUpdatableDataService<SecurityAppRecord, ULong, App, AppDAO> {

	private static final String APPLICATION = "Application";
	private static final String APPLICATION_ACCESS = "Application access";

	@Autowired
	private ClientService clientService;

	@Autowired
	private SecurityMessageResourceService messageResourceService;

	@Autowired
	private CacheService cacheService;

	@Autowired
	private PackageService packageService;

	@Autowired
	private RoleService roleService;

	private static final String CACHE_NAME_APP_READ_ACCESS = "appReadAccess";
	private static final String CACHE_NAME_APP_WRITE_ACCESS = "appWriteAccess";
	private static final String CACHE_NAME_APP_INHERITANCE = "appInheritance";
	private static final String CACHE_NAME_APP_FULL_INH_BY_APPCODE = "fullInhAppByCode";
	private static final String CACHE_NAME_APP_BY_APPCODE = "byAppCode";

	@PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
	@Override
	public Mono<App> create(App entity) {

		if (entity.getAppCode() != null && !StringUtil.onlyAlphabetAllowed(entity.getAppCode())) {
			return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
			        SecurityMessageResourceService.APP_CODE_NO_SPL_CHAR);
		}

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{

			        ULong clientId = ULong.valueOf(ca.getUser()
			                .getClientId());

			        if (entity.getClientId() == null)
				        entity.setClientId(clientId);

			        if (ca.isSystemClient() || entity.getClientId()
			                .equals(clientId))
				        return Mono.just(entity);

			        return this.clientService.isBeingManagedBy(clientId, entity.getClientId())
			                .flatMap(managed -> managed.booleanValue() ? Mono.just(entity) : Mono.empty());
		        },

		        (ca, app) -> app.getAppCode() == null ? this.dao.generateAppCode(app)
		                .map(app::setAppCode) : Mono.just(app),

		        (ca, app, appCodeAddedApp) -> super.create(appCodeAddedApp)

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.create"))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.FORBIDDEN_CREATE, APPLICATION)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
		        .flatMap(this.cacheService.evictAllFunction(ClientService.CACHE_NAME_CLIENT_URI));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	@Override
	public Mono<App> update(App entity) {
		return this.read(entity.getClientId())
		        .flatMap(e -> super.update(entity))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND, APPLICATION, entity.getId())))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
		        .flatMap(this.cacheService.evictAllFunction(ClientService.CACHE_NAME_CLIENT_URI))
		        .flatMap(e -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, e.getAppCode())
		                .map(x -> e));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	@Override
	public Mono<App> update(ULong key, Map<String, Object> fields) {
		return this.read(key)
		        .flatMap(e -> super.update(key, fields))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND, APPLICATION, key)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
		        .flatMap(e -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, e.getAppCode())
		                .map(x -> e));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_READ')")
	@Override
	public Mono<App> read(ULong id) {
		return super.read(id);
	}

	@PreAuthorize("hasAuthority('Authorities.Application_READ')")
	@Override
	public Mono<Page<App>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return super.readPageFilter(pageable, condition);
	}

	@PreAuthorize("hasAuthority('Authorities.Application_DELETE')")
	@Override
	public Mono<Integer> delete(ULong id) {
		return this.read(id)
		        .flatMap(e -> super.delete(id)
		                .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		                .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
		                .flatMap(x -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, e.getAppCode())
		                        .map(y -> x)))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND, APPLICATION, id)));
	}

	@Override
	protected Mono<App> updatableEntity(App entity) {

		return ((AppService) AopContext.currentProxy()).read(entity.getId())
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                existing.setAppName(entity.getAppName());

			                if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				                return Mono.just(existing);

			                return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                        .getClientId()), existing.getClientId())
			                        .flatMap(managed ->
									{
				                        if (managed.booleanValue())
					                        return Mono.just(existing);

				                        return messageResourceService
				                                .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                                .flatMap(msg -> Mono.error(() -> new GenericException(
				                                        HttpStatus.NOT_FOUND,
				                                        StringFormatter.format(msg, APPLICATION, entity.getId()))));
			                        });

		                }));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return ((AppService) AopContext.currentProxy()).read(key)
		        .flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
		                .flatMap(ca ->
						{

			                Map<String, Object> newMap = new HashMap<>();
			                newMap.put("appName", fields.get("appName"));

			                if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
				                return Mono.just(newMap);

			                return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                        .getClientId()), existing.getClientId())
			                        .flatMap(managed ->
									{
				                        if (managed.booleanValue())
					                        return Mono.just(newMap);

				                        return messageResourceService
				                                .getMessage(SecurityMessageResourceService.OBJECT_NOT_FOUND)
				                                .flatMap(msg -> Mono
				                                        .error(() -> new GenericException(HttpStatus.NOT_FOUND,
				                                                StringFormatter.format(msg, APPLICATION, key))));
			                        });

		                }));
	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
		return this.dao.hasReadAccess(appCode, clientCode);
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {
		return this.dao.hasWriteAccess(appCode, clientCode);
	}

	public Mono<Boolean> addClientAccess(ULong appId, ULong clientId, boolean writeAccess) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> ca.isSystemClient() ? Mono.just(true)
		                : this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                        .getClientId()), clientId),

		        (ca, clientAccess) ->
				{

			        if (!clientAccess.booleanValue())
				        return Mono.empty();

			        return this.read(appId)
			                .map(App::getClientId)
			                .flatMap(
			                        cid -> cid.equals(clientId) ? this.dao.addClientAccess(appId, clientId, writeAccess)
			                                : Mono.empty());
		        }, (ca, cliAccess, changed) -> this.evict(appId, clientId)
		                .map(e -> changed))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.addClientAccess"))
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.FORBIDDEN_CREATE, APPLICATION_ACCESS)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE));
	}

	public Mono<Boolean> addPackageAccess(ULong appId, ULong clientId, ULong packageId) {

		// check for duplicate entry

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> clientId != null ? Mono.just(clientId)
		                : this.dao.readById(appId)
		                        .map(App::getClientId),

		        (ca, appClientId) -> ca.isSystemClient() ? Mono.just(true)
		                : this.clientService.isBeingManagedBy(ULong.valueOf(ca.getUser()
		                        .getClientId()), appClientId)
		                        .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, appClientId, hasClientAccess) -> this.dao.hasAppEditAccess(appId, appClientId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, appClientId, hasClientAccess, editAccess) -> this.clientService
		                .checkClientHasPackage(appClientId, packageId)
		                .flatMap(hasAccess ->
						{

			                if (!hasAccess.booleanValue())
				                return this.packageService.read(packageId)
				                        .map(Package::getClientId)
				                        .flatMap(packageClientId -> Mono.just(packageClientId.equals(appClientId)))
				                        .flatMap(e -> Boolean.TRUE.equals(e) ? Mono.just(e) : Mono.empty());

			                return Mono.just(hasAccess);
		                })
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, appClientId, hasClientAccess, editAccess, hasPackageAccess) -> this.dao.addPackageAccess(appId,
		                appClientId, packageId)

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.addPackageAccess"))
		        .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.ASSIGN_PACKAGE_TO_CLIENT_AND_APP, packageId)));
	}

	public Mono<Boolean> removePackageAccess(ULong appId, ULong clientId, ULong packageId) {

		return this.dao.hasPackageAssignedWithApp(appId, clientId, packageId)
		        .flatMap(hasPackage ->
				{
			        if (!hasPackage.booleanValue())
				        return Mono.empty();

			        return FlatMapUtil.flatMapMono(

			                SecurityContextUtil::getUsersContextAuthentication,

			                ca -> ca.isSystemClient() ? Mono.just(true)
			                        : this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
			                                .getClientId()), clientId)
			                                .flatMap(BooleanUtil::safeValueOfWithEmpty),

			                (ca, hasClientAccess) -> this.dao.hasAppEditAccess(packageId, clientId)
			                        .flatMap(BooleanUtil::safeValueOfWithEmpty),

			                (ca, hasClientAccess, editAccess) -> this.clientService
			                        .checkClientHasPackage(clientId, packageId)
			                        .flatMap(hasAccess ->
									{

				                        if (!hasAccess.booleanValue())
					                        return this.packageService.read(packageId)
					                                .map(Package::getClientId)
					                                .flatMap(packageClientId -> Mono
					                                        .just(packageClientId.equals(clientId)))
					                                .flatMap(BooleanUtil::safeValueOfWithEmpty);

				                        return Mono.just(hasAccess);
			                        })
			                        .flatMap(BooleanUtil::safeValueOfWithEmpty),

			                (ca, hasClientAccess, editAccess, hasPackageAccess) -> this.dao.removePackageAccess(appId,
			                        clientId, packageId)

				)
			                .switchIfEmpty(
			                        Mono.defer(() -> this.messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
			                                SecurityMessageResourceService.REMOVE_PACKAGE_FROM_APP_ERROR)));

		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.removePackageAccess"))
		        .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.NO_PACKAGE_ASSIGNED_TO_APP)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE));

	}

	public Mono<Boolean> addRoleAccess(ULong appId, ULong clientId, ULong roleId) {

		// check for duplicate entry before calling the add method

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> clientId != null ? Mono.just(clientId)
		                : this.dao.readById(appId)
		                        .map(App::getClientId),

		        (ca, appClientId) -> ca.isSystemClient() ? Mono.just(true)
		                : this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
		                        .getClientId()), appClientId)
		                        .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, appClientId, sysOrManaged) -> this.dao.hasAppEditAccess(appId, clientId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, appClientId, sysOrManaged, appAccess) -> this.clientService
		                .checkRoleExistsOrCreatedForClient(clientId, roleId)
		                .flatMap(BooleanUtil::safeValueOfWithEmpty),

		        (ca, appClientId, sysOrManaged, appAccess, roleAccess) -> this.dao.addRoleAccess(appId, clientId,
		                roleId)

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.addRoleAccess"))
		        .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.ASSIGN_ROLE_TO_APP_ERROR)));
	}

	public Mono<Boolean> removeRoleAccess(ULong appId, ULong clientId, ULong roleId) {

		return this.dao.hasRoleAssignedWithApp(appId, clientId, roleId)
		        .flatMap(BooleanUtil::safeValueOfWithEmpty)
		        .flatMap(hasRole -> FlatMapUtil.flatMapMono(

		                SecurityContextUtil::getUsersContextAuthentication,

		                ca -> ca.isSystemClient() ? Mono.just(true)
		                        : this.clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
		                                .getClientId()), clientId)
		                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
		                (ca, sysOrManaged) -> this.dao.hasAppEditAccess(appId, clientId)
		                        .flatMap(BooleanUtil::safeValueOfWithEmpty),

		                (ca, sysOrManaged, appAccess) -> this.clientService
		                        .checkRoleExistsOrCreatedForClient(clientId, roleId)
		                        .flatMap(BooleanUtil::safeValueOfWithEmpty),

		                (ca, sysOrManaged, appAccess, roleAccess) -> this.dao.removeRoleAccess(appId, clientId, roleId)

				)
		                .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                        SecurityMessageResourceService.REMOVE_ROLE_FROM_APP_ERROR)))

				)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.removeRoleAccess"))
		        .switchIfEmpty(Mono.defer(() -> this.messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.NO_ROLE_ASSIGNED_TO_APP)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE));

	}

	public Mono<Boolean> evict(ULong appId, ULong clientId) {

		return FlatMapUtil.flatMapMono(

		        () -> this.read(appId),

		        app -> this.clientService.getClientTypeNCode(clientId),

		        (app, typNCode) -> cacheService.evict(CACHE_NAME_APP_WRITE_ACCESS, app.getAppCode(), ":",
		                typNCode.getT2()),

		        (app, typNCode, removed) -> cacheService.evict(CACHE_NAME_APP_READ_ACCESS, app.getAppCode(), ":",
		                typNCode.getT2()))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.evict"));
	}

	public Mono<Boolean> removeClient(ULong appId, ULong accessId) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        if (ca.isSystemClient()) {
				        return this.dao.removeClientAccess(appId, accessId);
			        }

			        return this.dao.readById(appId)
			                .flatMap(e ->
							{

				                if (!e.getClientId()
				                        .toBigInteger()
				                        .equals(ca.getUser()
				                                .getClientId()))
					                return Mono.empty();

				                return this.dao.removeClientAccess(appId, accessId);
			                });
		        })
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.UNABLE_TO_DELETE, APPLICATION_ACCESS, accessId)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE));
	}

	public Mono<Boolean> updateClientAccess(ULong accessId, boolean writeAccess) {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .flatMap(ca ->
				{

			        if (ca.isSystemClient()) {
				        return this.dao.updateClientAccess(accessId, writeAccess);
			        }

			        return this.dao.readById(accessId)
			                .flatMap(e ->
							{

				                if (!e.getClientId()
				                        .toBigInteger()
				                        .equals(ca.getUser()
				                                .getClientId()))
					                return Mono.empty();

				                return this.dao.updateClientAccess(accessId, writeAccess);
			                });
		        })
		        .switchIfEmpty(Mono.defer(() -> messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
		                SecurityMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, APPLICATION_ACCESS, accessId)))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_FULL_INH_BY_APPCODE))
		        .flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE));
	}

	public Mono<List<String>> appInheritance(String appCode, String urlClientCode, String clientCode) {

		return this.cacheService.cacheValueOrGet(CACHE_NAME_APP_INHERITANCE,
		        () -> this.dao.appInheritance(appCode, urlClientCode, clientCode), appCode, ":", urlClientCode, ":",
		        clientCode);
	}

	public Mono<App> getAppByCode(String appCode) {
		return this.cacheService.cacheValueOrGet(CACHE_NAME_APP_BY_APPCODE, () -> this.dao.getByAppCode(appCode),
		        appCode);
	}

	public Mono<List<Package>> getPackagesAssignedToApp(String appCode, ULong clientId) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.getAppByCode(appCode),

		        (ca, app) -> this.dao
		                .fetchPackagesBasedOnClient(clientId == null ? app.getClientId() : clientId, app.getId())
		                .map(Object.class::cast)
		                .collectList(),

		        (ca, app, packages) ->
				{

			        FilterCondition cond = new FilterCondition();
			        cond.setField("id")
			                .setMultiValue(packages)
			                .setOperator(FilterConditionOperator.IN);

			        return this.packageService.readAllFilter(cond)
			                .collectList();
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getPackagesAssignedToClient"));

	}

	public Mono<List<Role>> getRolesAssignedToApp(String appCode, ULong clientId) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.getAppByCode(appCode),

		        (ca, app) -> this.dao.fetchRolesBasedOnClient(clientId, app.getClientId())
		                .map(Object.class::cast)
		                .collectList(),

		        (ca, app, roles) ->
				{

			        FilterCondition cond = new FilterCondition();
			        cond.setField("id")
			                .setMultiValue(roles)
			                .setOperator(FilterConditionOperator.IN);

			        return this.roleService.readAllFilterWithReadPermissions(cond)
			                .collectList();
		        }

		)
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getRolesAssignedToApp"));

	}

	public Mono<List<Client>> getAppClients(String appCode, boolean onlyWriteAccess) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.cacheService.cacheValueOrGet(CACHE_NAME_APP_FULL_INH_BY_APPCODE,

		                () -> FlatMapUtil.flatMapMono(

		                        () -> this.getAppByCode(appCode),

		                        app ->
								{

			                        if (!ca.isSystemClient()) {
				                        // TODO: This code should return all the app urls of all the clients it has
				                        // reporting.
				                        return this.clientService.getClientInfoById(ca.getUser()
				                                .getClientId())
				                                .map(List::of);
			                        }

			                        return this.dao.getClientIdsWithAccess(appCode, onlyWriteAccess)
			                                .map(ULong::toBigInteger)
			                                .flatMap(this.clientService::getClientInfoById)
			                                .collectList();
		                        })
		                        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getAppClients"))

		                , ca.getClientCode(), ":", appCode))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getAppClients"));
	}

	public Mono<Boolean> addClientAccessAfterRegistration(String urlAppCode, ULong clientId, boolean isWriteAccess) {

		return this.getAppByCode(urlAppCode)
		        .flatMap(a -> this.dao.addClientAccess(a.getId(), clientId, isWriteAccess));
	}

}
