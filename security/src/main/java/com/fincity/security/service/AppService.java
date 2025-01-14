package com.fincity.security.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;
import com.fincity.security.model.AppDependency;
import com.fincity.security.model.PropertiesResponse;
import com.fincity.security.model.TransportPOJO.AppTransportProperty;
import com.google.common.base.Functions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class AppService extends AbstractJOOQUpdatableDataService<SecurityAppRecord, ULong, App, AppDAO> {

	private static final String APPLICATION = "Application";
	private static final String APPLICATION_ACCESS = "Application access";

	private final ClientService clientService;
	private final SecurityMessageResourceService messageResourceService;
	private final CacheService cacheService;
	private final AppRegistrationDAO appRegistrationDao;

	private static final String CACHE_NAME_APP_READ_ACCESS = "appReadAccess";
	private static final String CACHE_NAME_APP_WRITE_ACCESS = "appWriteAccess";
	private static final String CACHE_NAME_APP_INHERITANCE = "appInheritance";
	private static final String CACHE_NAME_APP_BY_APPCODE = "byAppCode";
	private static final String CACHE_NAME_APP_BY_APPID = "byAppId";
	private static final String CACHE_NAME_APP_BY_APPCODE_EXPLICIT = "byAppCodeExplicit";
	private static final String CACHE_NAME_APP_DEPENDENCIES = "appDependencies";
	private static final String CACHE_NAME_APP_DEP_LIST = "appDepList";

	public static final String AC = "appCode";
	public static final String APP_PROP_REG_TYPE = "REGISTRATION_TYPE";
	public static final String APP_PROP_REG_TYPE_VERIFICATION = "REGISTRATION_TYPE_VERIFICATION";
	public static final String APP_PROP_REG_TYPE_NO_VERIFICATION = "REGISTRATION_TYPE_NO_VERIFICATION";
	public static final String APP_PROP_REG_TYPE_NO_REGISTRATION = "REGISTRATION_TYPE_NO_REGISTRATION";

	public static final String APP_PROP_URL_SUFFIX = "URL_SUFFIX";
	public static final String APP_PROP_URL = "URL";

	public static final String APP_ACCESS_TYPE = "appAccessType";
	public static final String APP_USAGE_TYPE = "appUsageType";
	public static final String APP_NAME = "appName";
	public static final String THUMB_URL = "thumbUrl";

	public AppService(ClientService clientService, SecurityMessageResourceService messageResourceService,
			CacheService cacheService, AppRegistrationDAO appRegistrationDao) {

		this.clientService = clientService;
		this.messageResourceService = messageResourceService;
		this.cacheService = cacheService;
		this.appRegistrationDao = appRegistrationDao;
	}

	@PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
	@Override
	public Mono<App> create(App entity) {

		if (entity.getAppCode() != null && !StringUtil.onlyAlphabetAllowed(entity.getAppCode())) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.APP_CODE_NO_SPL_CHAR);
		}

		Mono<App> normalFlow = FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					ULong clientId = ULong.valueOf(ca.getUser()
							.getClientId());

					if (entity.getClientId() == null)
						entity.setClientId(clientId);

					if (ca.isSystemClient() || entity.getClientId()
							.equals(clientId))
						return Mono.just(entity);

					return this.clientService.isBeingManagedBy(clientId, entity.getClientId())
							.flatMap(managed -> BooleanUtil.safeValueOf(managed) ? Mono.just(entity) : Mono.empty());
				},

				(ca, app) -> app.getAppCode() == null ? this.dao.generateAppCode(app)
						.map(app::setAppCode) : Mono.just(app),

				(ca, app, appCodeAddedApp) -> super.create(appCodeAddedApp));

		Mono<App> explicitAppCreationFlow = FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.clientService.getManagedClientOfClientById(ULongUtil.valueOf(ca.getUser()
						.getClientId())).map(Client::getId).switchIfEmpty(this.clientService.getSystemClientId()),

				(ca, managedClientId) -> entity.getAppCode() == null
						? this.dao.generateAppCode(entity).map(entity::setAppCode)
						: Mono.just(entity),

				(ca, managedClientId, appCodeAddedApp) -> super.create(appCodeAddedApp.setClientId(managedClientId)),

				(ca, mci, ac, created) -> this.dao.addClientAccess(created.getId(), ULongUtil.valueOf(ca.getUser()
						.getClientId()), true)
						.flatMap(x -> Mono.just(created)));

		return (entity.getAppAccessType() == SecurityAppAppAccessType.EXPLICIT ? explicitAppCreationFlow : normalFlow)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.create"))
				.switchIfEmpty(
						messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.FORBIDDEN_CREATE,
								APPLICATION))

				.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
				.flatMap(this.cacheService.evictAllFunction(ClientService.CACHE_NAME_CLIENT_URI));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	@Override
	public Mono<App> update(App entity) {
		return this.read(entity.getId())
				.flatMap(e -> super.update(entity))
				.switchIfEmpty(
						messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								AbstractMessageService.OBJECT_NOT_FOUND, APPLICATION, entity
										.getId()))

				.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
				.flatMap(this.cacheService.evictAllFunction(ClientService.CACHE_NAME_CLIENT_URI))
				.flatMap(e -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, e.getAppCode())
						.flatMap(x -> this.cacheService.evict(CACHE_NAME_APP_BY_APPID, e.getId()))
						.map(x -> e));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
	@Override
	public Mono<App> update(ULong key, Map<String, Object> fields) {
		return this.read(key)
				.flatMap(e -> super.update(key, fields))
				.switchIfEmpty(
						messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								AbstractMessageService.OBJECT_NOT_FOUND, APPLICATION,
								key))

				.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
				.flatMap(e -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, e.getAppCode())
						.flatMap(x -> this.cacheService.evict(CACHE_NAME_APP_BY_APPID, e.getId()))
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

		Mono<Integer> what = FlatMapUtil.flatMapMono(
				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.read(id),

				(ca, app) -> {
					if (ca.isSystemClient())
						return Mono.just(true);

					if (app.getClientId().equals(id))
						return Mono.just(true);

					return this.clientService
							.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()), app.getClientId());
				},

				(ca, app, hasAccess) -> super.delete(id)

						.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
						.flatMap(x -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, app.getAppCode())
								.flatMap(z -> this.cacheService.evict(CACHE_NAME_APP_BY_APPID, app.getId()))
								.map(y -> x)));

		return what
				.switchIfEmpty(
						messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								AbstractMessageService.OBJECT_NOT_FOUND, APPLICATION, id))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.delete"));
	}

	@PreAuthorize("hasAuthority('Authorities.Application_DELETE')")
	public Mono<Boolean> deleteEverything(ULong id, boolean forceDelete) {

		Mono<Boolean> what = FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.read(id),

				(ca, app) -> {
					if (ca.isSystemClient() || app.getClientId().equals(id)
							|| app.getClientId().toBigInteger().equals(ca.getLoggedInFromClientId())) {
						if (forceDelete)
							return Mono.just(true);
						return this.isNoneUsingTheAppOtherThan(id, ca.getUser().getClientId());
					}

					return Mono.just(false);
				},

				(ca, app, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? Mono.just(true) : Mono.empty(),

				(ca, app, hasAccess, delete) -> this.dao.deleteEverythingRelated(id, app.getAppCode())

						.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE))
						.flatMap(x -> this.cacheService.evict(CACHE_NAME_APP_BY_APPCODE, app.getAppCode())
								.flatMap(z -> this.cacheService.evict(CACHE_NAME_APP_BY_APPID, app.getId()))
								.map(y -> x))

		);

		return what
				.switchIfEmpty(
						messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								AbstractMessageService.OBJECT_NOT_FOUND, APPLICATION, id))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.deleteEverything"));
	}

	public Mono<Boolean> isNoneUsingTheAppOtherThan(ULong id, BigInteger clientId) {
		return this.dao.isNoneUsingTheAppOtherThan(id, clientId);
	}

	@Override
	protected Mono<App> updatableEntity(App entity) {

		return this.read(entity.getId())
				.flatMap(existing -> SecurityContextUtil.getUsersContextAuthentication()
						.flatMap(ca -> {

							existing.setAppName(entity.getAppName());
							existing.setAppAccessType(entity.getAppAccessType());
							existing.setThumbUrl(entity.getThumbUrl());
							existing.setAppUsageType(entity.getAppUsageType());

							if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
								return Mono.just(existing);

							return clientService.isBeingManagedBy(ULongUtil.valueOf(ca.getUser()
									.getClientId()), existing.getClientId())
									.flatMap(managed -> {
										if (BooleanUtil.safeValueOf(managed))
											return Mono.just(existing);

										return messageResourceService
												.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
												.flatMap(msg -> Mono.error(() -> new GenericException(
														HttpStatus.NOT_FOUND,
														StringFormatter.format(msg, APPLICATION, entity.getId()))));
									});

						}));
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(key),

				app -> validateFields(fields),

				(app, updatableFields) -> SecurityContextUtil.getUsersContextAuthentication(),

				(app, updatableFields, ca) -> {

					if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
						return Mono.just(updatableFields);

					return messageResourceService.getMessage(AbstractMessageService.OBJECT_NOT_FOUND)
							.flatMap(msg -> Mono.error(() -> new GenericException(HttpStatus.NOT_FOUND,
									StringFormatter.format(msg, APPLICATION, key))));

				}

		);

	}

	private Mono<Map<String, Object>> validateFields(Map<String, Object> fields) {

		Map<String, Object> updatableFields = new HashMap<>();

		if (fields.containsKey(APP_NAME))
			updatableFields.put(APP_NAME, fields.get(APP_NAME));

		if (fields.containsKey(THUMB_URL))
			updatableFields.put(THUMB_URL, fields.get(THUMB_URL));

		if (fields.containsKey(APP_ACCESS_TYPE))
			updatableFields.put(APP_ACCESS_TYPE, fields.get(APP_ACCESS_TYPE));

		if (fields.containsKey(APP_USAGE_TYPE))
			updatableFields.put(APP_USAGE_TYPE, fields.get(APP_USAGE_TYPE));

		return Mono.just(updatableFields);

	}

	public Mono<Boolean> hasReadAccess(String appCode, String clientCode) {
		return this.dao.hasReadAccess(appCode, clientCode);
	}

	public Mono<Boolean> hasWriteAccess(String appCode, String clientCode) {
		return this.dao.hasWriteAccess(appCode, clientCode);
	}

	public Mono<Boolean> hasWriteAccess(ULong appId, ULong clientId) {
		return this.dao.hasWriteAccess(appId, clientId);
	}

	public Mono<Boolean> hasDeleteAccess(String appCode) {
		return FlatMapUtil.flatMapMono(
				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getAppByCode(appCode),

				(ca, app) -> {
					if (ca.isSystemClient())
						return Mono.just(true);

					if (app.getClientId().equals(ULongUtil.valueOf(ca.getUser().getClientId())))
						return Mono.just(true);

					return this.clientService
							.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()), app.getClientId());
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.hasDeleteAccess"));
	}

	public Mono<Boolean> addClientAccess(ULong appId, ULong clientId, boolean writeAccess) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.read(appId),

				(ca, app) -> {

					ULong usersClientId = ULongUtil.valueOf(ca.getUser()
							.getClientId());

					if (ca.isSystemClient()
							&& app.getClientId().equals(usersClientId)) {
						return this.dao.addClientAccess(appId, clientId, writeAccess);
					}

					return this.clientService.isBeingManagedBy(app.getClientId(), clientId)
							.flatMap(managed -> {

								if (!BooleanUtil.safeValueOf(managed))
									return Mono.empty();

								if (!usersClientId.equals(app.getClientId())
										&& SecurityAppAppAccessType.ANY != app.getAppAccessType())
									return Mono.empty();

								return this.dao.addClientAccess(appId, clientId,
										writeAccess);
							});

				},

				(ca, cliAccess, changed) -> this.evict(appId, clientId))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.addClientAccess"))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						SecurityMessageResourceService.FORBIDDEN_CREATE, APPLICATION_ACCESS))

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
				.flatMap(ca -> {

					if (ca.isSystemClient()) {
						return this.dao.removeClientAccess(appId, accessId);
					}

					return this.dao.readById(appId)
							.flatMap(e -> {

								if (!e.getClientId()
										.toBigInteger()
										.equals(ca.getUser()
												.getClientId()))
									return Mono.empty();

								return this.dao.removeClientAccess(appId, accessId);
							});
				})
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg), APPLICATION_ACCESS,
						accessId))

				.flatMap(this.cacheService.evictAllFunction(CACHE_NAME_APP_INHERITANCE));
	}

	public Mono<Boolean> updateClientAccess(ULong accessId, boolean writeAccess) {

		return SecurityContextUtil.getUsersContextAuthentication()
				.flatMap(ca -> {

					if (ca.isSystemClient()) {
						return this.dao.updateClientAccess(accessId, writeAccess);
					}

					return this.dao.readById(accessId)
							.flatMap(e -> {

								if (!e.getClientId()
										.toBigInteger()
										.equals(ca.getUser()
												.getClientId()))
									return Mono.empty();

								return this.dao.updateClientAccess(accessId, writeAccess);
							});
				})
				.switchIfEmpty(
						messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, APPLICATION_ACCESS,
								accessId))

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

	public Mono<ULong> getAppId(String appCode) {
		return this.getAppByCode(appCode).map(App::getId);
	}

	public Mono<App> getAppById(ULong appId) {
		if (appId == null)
			return Mono.empty();
		return this.cacheService.cacheValueOrGet(CACHE_NAME_APP_BY_APPID, () -> this.read(appId), appId);
	}

	public Mono<App> getAppByCodeCheckAccess(String appCode) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getAppByCode(appCode),

				(ca, app) -> {

					if (ca.isSystemClient())
						return Mono.just(app);

					return this.hasReadAccess(appCode, ca.getClientCode())
							.flatMap(hasAccess -> BooleanUtil.safeValueOf(hasAccess) ? Mono.just(app) : Mono.empty());
				}

		);
	}

	public Mono<Page<Client>> getAppClients(String appCode, boolean onlyWriteAccess, String name, Pageable pageable) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca.isSystemClient() ? Mono.just(true) : this.hasWriteAccess(appCode, ca.getClientCode()),

				(ca, hasAccess) -> {
					if (!BooleanUtil.safeValueOf(hasAccess))
						return Mono.just(Page.empty(pageable));

					return this.dao.getAppClients(appCode, onlyWriteAccess, name,
							ca.isSystemClient() ? null : ULong.valueOf(ca.getUser().getClientId()), pageable);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getAppClients"));
	}

	public Mono<Boolean> addClientAccessAfterRegistration(String urlAppCode, ULong urlClientId, Client client) {

		return FlatMapUtil.flatMapMono(

				() -> this.getAppByCode(urlAppCode),

				app -> this.clientService.getClientLevelType(client.getId(), app.getId()),

				(app, levelType) -> this.appRegistrationDao.getAppIdsForRegistration(app.getId(),
						app.getClientId(), urlClientId, client.getTypeCode(), levelType, client.getBusinessType()),

				(app, levelType, appAccessTuples) -> {

					Mono<List<Boolean>> mons = Flux.fromIterable(appAccessTuples)
							.flatMap(tup -> this.dao.addClientAccess(tup.getT1(), client.getId(), tup.getT2()))
							.collectList();

					return mons.map(e -> true);
				}

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.addClientAccessAfterRegistration"));
	}

	public Mono<Map<ULong, Map<String, AppProperty>>> getProperties(ULong clientId, ULong appId, String appCode,
			String propName) {

		if (appId == null && StringUtil.safeIsBlank(appCode)) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.MANDATORY_APP_ID_CODE);
		}

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isSystemClient())
						return Mono.just(clientId == null ? List.<ULong>of() : List.of(clientId));

					ULong userClientId = ULongUtil.valueOf(ca.getUser()
							.getClientId());

					if (clientId == null || userClientId.equals(clientId))
						return Mono.just(List.of(userClientId));

					return this.clientService.isBeingManagedBy(userClientId, clientId)
							.flatMap(managed -> BooleanUtil.safeValueOf(managed) ? Mono.just(List.of(clientId))
									: Mono.empty());
				},

				(ca, idList) -> this.dao.getProperties(idList, appId, appCode, propName))
				.defaultIfEmpty(Map.of())
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getProperties"));
	}

	public Mono<PropertiesResponse> getPropertiesWithClients(ULong clientId, ULong appId, String appCode,
			String propName) {

		return FlatMapUtil.flatMapMono(

				() -> this.getProperties(clientId, appId, appCode, propName),

				props -> {

					if (props.isEmpty())
						return Mono.just(Map.<ULong, Client>of());

					Mono<List<Client>> clients = this.clientService.getClientsBy(new ArrayList<>(props.keySet()));

					return clients
							.map(lst -> lst.stream().collect(Collectors.toMap(Client::getId, Functions.identity())));
				},

				(props, clients) -> Mono.just(new PropertiesResponse().setProperties(props).setClients(clients))

		)
				.defaultIfEmpty(new PropertiesResponse())
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.getPropertiesWithClients"));
	}

	public Mono<Boolean> updateProperty(AppProperty property) {

		if (property.getAppId() == null || StringUtil.safeIsBlank(property.getName())) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.MANDATORY_APP_ID_NAME);
		}

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isSystemClient())
						return Mono.just(Boolean.TRUE);

					ULong userClientId = ULongUtil.valueOf(ca.getUser()
							.getClientId());

					if (userClientId.equals(property.getClientId()))
						return Mono.just(Boolean.TRUE);

					return this.clientService.isBeingManagedBy(userClientId, property.getClientId())
							.flatMap(managed -> BooleanUtil.safeValueOf(managed) ? Mono.just(Boolean.TRUE)
									: Mono.empty());
				},

				(ca, hasAccess) -> this.dao.hasWriteAccess(property.getAppId(), property.getClientId()),

				(ca, hasAccess, writeAccess) -> {

					if (!BooleanUtil.safeValueOf(writeAccess))
						return this.messageResourceService.throwMessage(
								e -> new GenericException(HttpStatus.FORBIDDEN, e),
								SecurityMessageResourceService.FORBIDDEN_CREATE, "Application property");

					return this.dao.updateProperty(property);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.updateProperty"));
	}

	public Mono<Boolean> deletePropertyById(ULong propertyId) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.getPropertyById(propertyId),

				(ca, property) -> {

					if (ca.isSystemClient())
						return Mono.just(Boolean.TRUE);

					ULong userClientId = ULongUtil.valueOf(ca.getUser()
							.getClientId());

					if (userClientId.equals(property.getClientId()))
						return Mono.just(Boolean.TRUE);

					return this.clientService.isBeingManagedBy(userClientId, property.getClientId())
							.flatMap(managed -> BooleanUtil.safeValueOf(managed) ? Mono.just(Boolean.TRUE)
									: Mono.empty());
				},

				(ca, property, hasAccess) -> this.dao.hasWriteAccess(property.getAppId(), property.getClientId()),

				(ca, property, hasAccess, writeAccess) -> {

					if (!BooleanUtil.safeValueOf(writeAccess))
						return Mono.just(false);

					return this.dao.deletePropertyById(propertyId);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.deletePropertyById"));
	}

	public Mono<Boolean> deleteProperty(ULong clientId, ULong appId, String name) {

		if (appId == null) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.MANDATORY_APP_ID_CODE);
		}

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isSystemClient())
						return Mono.just(Boolean.TRUE);

					ULong userClientId = ULongUtil.valueOf(ca.getUser()
							.getClientId());

					if (userClientId.equals(clientId))
						return Mono.just(Boolean.TRUE);

					return this.clientService.isBeingManagedBy(userClientId, clientId)
							.flatMap(managed -> BooleanUtil.safeValueOf(managed) ? Mono.just(Boolean.TRUE)
									: Mono.empty());
				},

				(ca, hasAccess) -> this.dao.hasWriteAccess(appId, clientId),

				(ca, hasAccess, writeAccess) -> {

					if (!BooleanUtil.safeValueOf(writeAccess))
						return Mono.just(false);

					return this.dao.deleteProperty(clientId, appId, name);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.deleteProperty"));
	}

	public Mono<Boolean> createPropertiesFromTransport(ULong appId, List<AppTransportProperty> properties) {

		if (properties == null || properties.isEmpty())
			return Mono.just(true);

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Flux.fromIterable(properties)
						.flatMap(p -> this.dao.createPropertyFromTransport(appId,
								ULongUtil.valueOf(ca.getUser().getClientId()), p.getPropertyName(),
								p.getPropertyValue()))
						.map(e -> true).collectList().map(e -> true));
	}

	public Mono<Tuple2<String, Boolean>> findBaseClientCodeForOverride(String applicationCode) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getAppByCode(applicationCode),

				(ca, app) -> (ca.getUser().getClientId().equals(app.getClientId().toBigInteger()) ? Mono.empty()
						: this.clientService.readInternal(app.getClientId())),

				(ca, app, appClient) -> {

					if (app.getAppAccessType() == SecurityAppAppAccessType.EXPLICIT) {
						return this.hasWriteAccess(applicationCode, ca.getClientCode()).flatMap(
								e -> BooleanUtil.safeValueOf(e) ? Mono.just(Tuples.of(appClient.getCode(), true))
										: Mono.empty());
					}

					return Mono.just(Tuples.of(appClient.getCode(), false));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.findBaseClientCodeForOverride"));
	}

	public Mono<Page<App>> findAnyAppsByPage(Pageable pageable, AbstractCondition condition) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isSystemClient() || ca.getLoggedInFromClientId().equals(ca.getUser().getClientId()))
						return Mono.just(Page.empty());

					return this.dao.readAnyAppsPageFilter(pageable, condition,
							ULongUtil.valueOf(ca.getLoggedInFromClientId()));

				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.findAnyAppsByPage"));
	}

	public Mono<com.fincity.saas.commons.security.dto.App> getAppExplicitInfoByCode(String appCode) {

		Mono<com.fincity.saas.commons.security.dto.App> appMono = FlatMapUtil.flatMapMono(
				() -> this.dao.getByAppCodeExplicitInfo(appCode),

				app -> Mono.just(app.getClientId()).map(ULong::valueOf)
						.flatMap(this.clientService::readInternal)
						.map(Client::getCode)
						.map(app::setClientCode),

				(x, app) -> Mono.justOrEmpty(app.getExplicitClientId()).map(ULong::valueOf)
						.flatMap(this.clientService::readInternal)
						.map(Client::getCode)
						.map(app::setExplicitOwnerClientCode)
						.defaultIfEmpty(app));

		return this.cacheService.cacheValueOrGet(CACHE_NAME_APP_BY_APPCODE_EXPLICIT, () -> appMono, appCode);
	}

	public Mono<List<AppDependency>> getAppDependencies(String appCode) {

		return this.cacheService.cacheEmptyValueOrGet(CACHE_NAME_APP_DEPENDENCIES,
				() -> this.dao.getByAppCode(appCode).map(App::getId).flatMap(this.dao::getAppDependencies), appCode);
	}

	public Mono<AppDependency> addAppDependency(String appCode, String dependentAppCode) {

		if (appCode.equals(dependentAppCode) || StringUtil.safeIsBlank(appCode)
				|| StringUtil.safeIsBlank(dependentAppCode))
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.APP_DEPENDENCY_SAME_APP_CODE);

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.getByAppCode(appCode),

				(ca, app) -> this.dao.getByAppCode(dependentAppCode),

				(ca, app, depApp) -> {
					if (!app.getClientId().equals(depApp.getClientId()))
						return Mono.empty();

					if (ca.isSystemClient())
						return Mono.just(true);

					return this.clientService
							.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()), app.getClientId())
							.flatMap(e -> BooleanUtil.safeValueOf(e) ? Mono.just(true) : Mono.empty());
				},

				(ca, app, depApp, hasAccess) -> this.dao.addAppDependency(app.getId(), depApp.getId()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.addAppDependency"))
				.flatMap(this.cacheService.evictFunction(CACHE_NAME_APP_DEPENDENCIES, appCode))
				.flatMap(this.cacheService.evictFunction(CACHE_NAME_APP_DEP_LIST, appCode))
				.switchIfEmpty(
						this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.FORBIDDEN_CREATE, "App Dependency"));
	}

	public Mono<Boolean> removeAppDependency(String appCode, String dependentAppCode) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.dao.getByAppCode(appCode),

				(ca, app) -> this.dao.getByAppCode(dependentAppCode),

				(ca, app, depApp) -> {
					if (!app.getClientId().equals(depApp.getClientId()))
						return Mono.empty();

					if (ca.isSystemClient())
						return Mono.just(true);

					return this.clientService
							.isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()), app.getClientId())
							.flatMap(e -> BooleanUtil.safeValueOf(e) ? Mono.just(true) : Mono.empty());
				},

				(ca, app, depApp, hasAccess) -> this.dao.removeAppDependency(app.getId(), depApp.getId()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppService.removeAppDependency"))
				.flatMap(this.cacheService.evictFunction(CACHE_NAME_APP_DEP_LIST, appCode))
				.flatMap(this.cacheService.evictFunction(CACHE_NAME_APP_DEPENDENCIES, appCode))
				.switchIfEmpty(
						this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								SecurityMessageResourceService.FORBIDDEN_CREATE, "App Dependency"));
	}

}
