package com.fincity.saas.notification.service;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.dao.UserPreferenceDao;
import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferencesRecord;

import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class UserPreferenceService
		extends AbstractCodeService<NotificationUserPreferencesRecord, ULong, UserPreference, UserPreferenceDao> {

	private static final String USER_PREFERENCE = "user_preference";

	private static final String USER_PREFERENCE_CACHE = "userPreference";

	protected final NotificationMessageResourceService messageResourceService;
	private final CacheService cacheService;

	@Getter
	private IFeignSecurityService securityService;

	protected UserPreferenceService(NotificationMessageResourceService messageResourceService,
	                                CacheService cacheService) {
		this.messageResourceService = messageResourceService;
		this.cacheService = cacheService;
	}

	@Autowired
	public void setSecurityService(IFeignSecurityService securityService) {
		this.securityService = securityService;
	}

	private String getUserPreferenceName() {
		return USER_PREFERENCE;
	}

	private String getUserPreferenceCacheName() {
		return USER_PREFERENCE_CACHE;
	}

	private String getCacheKeys(ULong appId, ULong userId) {
		return appId + ":" + userId;
	}

	@Override
	public Mono<UserPreference> getByCode(String code) {
		return this.dao.getByCode(code);
	}

	@Override
	public Mono<UserPreference> update(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> key != null ? this.read(key) : this.getUserPreference(ca),

						(ca, entity) -> this.canUpdatePreference(ca),

						(ca, entity, canUpdate) -> super.update(key, fields),

						(ca, entity, canUpdate, updated) -> cacheService.evict(this.getUserPreferenceCacheName(),
								this.getCacheKeys(updated.getAppId(), updated.getUserId())).map(evicted -> updated))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getUserPreferenceName()));
	}

	@Override
	public Mono<UserPreference> update(UserPreference entity) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.read(entity.getId()),

						(ca, uEntity) -> this.canUpdatePreference(ca),

						(ca, uEntity, canUpdate) -> super.update(uEntity),

						(ca, uEntity, canUpdate, updated) -> cacheService.evict(this.getUserPreferenceCacheName(),
								this.getCacheKeys(updated.getAppId(), updated.getUserId())).map(evicted -> updated))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getUserPreferenceName()));
	}

	@Override
	protected Mono<UserPreference> updatableEntity(UserPreference entity) {

		return this.read(entity.getId())
				.map(e -> {
					e.setEnabled(entity.isEnabled());
					e.setPreferences(entity.getPreferences());
					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.remove("id");
		fields.remove("appId");
		fields.remove("userId");
		fields.remove("code");
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}

	@Override
	public Mono<UserPreference> create(UserPreference entity) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.updateIdentifiers(ca, entity),

						(ca, uEntity) -> this.canUpdatePreference(ca).flatMap(BooleanUtil::safeValueOfWithEmpty),

						(ca, uEntity, canCreate) -> super.create(uEntity),

						(ca, uEntity, canCreate, created) -> cacheService.evict(this.getUserPreferenceCacheName(),
								this.getCacheKeys(created.getAppId(), created.getUserId())).map(evicted -> created))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_CREATE, getUserPreferenceName()));
	}

	@Override
	public Mono<UserPreference> read(ULong id) {

		if (id == null)
			return Mono.empty();

		return super.read(id);
	}

	@Override
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.read(id),

						(ca, entity) -> this.canUpdatePreference(ca),

						(ca, entity, canDelete) -> super.delete(id),

						(ca, entity, canUpdate, deleted) -> cacheService.evict(this.getUserPreferenceCacheName(),
								this.getCacheKeys(entity.getAppId(), entity.getUserId())).map(evicted -> deleted),

						(ca, entity, canUpdate, deleted, evicted) -> Mono.just(deleted)
				)
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getUserPreferenceName()));
	}

	private Mono<UserPreference> updateIdentifiers(ContextAuthentication ca, UserPreference entity) {

		return FlatMapUtil.flatMapMonoWithNull(

						() -> this.securityService.getAppByCode(ca.getUrlAppCode()).map(app -> ULongUtil.valueOf(app.getId())),

						appId -> {

							entity.init();

							if (entity.getAppId() == null)
								entity.setAppId(appId);

							if (entity.getUserId() == null)
								entity.setUserId(ULongUtil.valueOf(ca.getUser().getId()));

							return Mono.just(entity);
						},
						(appId, uEntity) -> this.getUserPreferenceInternal(uEntity.getAppId(),
								uEntity.getUserId()),

						(appId, uEntity, userPreference) -> {

							if (userPreference != null)
								return messageResourceService.throwMessage(
										msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
										NotificationMessageResourceService.FORBIDDEN_CREATE, this.getUserPreferenceName());

							return Mono.just(uEntity);
						})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserPreferenceService.updateIdentifiers"));
	}

	private Mono<Boolean> canUpdatePreference(ContextAuthentication ca) {

		return FlatMapUtil.flatMapMono(

						() -> this.securityService.getAppByCode(ca.getUrlAppCode()),

						app -> FlatMapUtil.flatMapMonoConsolidate(
								() -> this.securityService.isBeingManagedById(ca.getLoggedInFromClientId(), app.getClientId()),
								isManaged -> this.securityService.hasWriteAccess(ca.getUrlAppCode(),
										ca.getLoggedInFromClientCode()),
								(isManaged, hasEditAccess) -> Mono.just(ca.isSystemClient())),

						(app, managedOrEdit) -> Mono.just(
								managedOrEdit.getT1() || managedOrEdit.getT2() || managedOrEdit.getT3()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserPreferenceService.canUpdatePreference"))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	public Mono<UserPreference> getUserPreference() {
		return SecurityContextUtil.getUsersContextAuthentication().flatMap(this::getUserPreference);
	}

	public Mono<UserPreference> getUserPreference(ContextAuthentication ca) {
		return this.getAppUserId(ca.getUrlAppCode(), ULongUtil.valueOf(ca.getUser().getId()))
				.flatMap(appUserId -> this.getUserPreference(appUserId.getT1(), appUserId.getT2()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"UserPreferenceService.getUserPreference[ContextAuthentication]"));
	}

	public Mono<UserPreference> getUserPreference(String appCode, ULong userId) {
		return this.getAppUserId(appCode, userId)
				.flatMap(appUserId -> this.getUserPreference(appUserId.getT1(), appUserId.getT2()))
				.contextWrite(
						Context.of(LogUtil.METHOD_NAME, "UserPreferenceService.getUserPreference[String, ULong]"));
	}

	public Mono<UserPreference> getUserPreference(ULong appId, ULong userId) {
		return this.getUserPreferenceInternal(appId, userId)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "UserPreferenceService.getUserPreference[ULong, ULong]"));
	}

	private Mono<UserPreference> getUserPreferenceInternal(ULong appId, ULong userId) {
		return this.cacheService.cacheEmptyValueOrGet(this.getUserPreferenceCacheName(),
				() -> this.dao.getUserPreference(appId, userId), this.getCacheKeys(appId, userId));
	}

	private Mono<Tuple2<ULong, ULong>> getAppUserId(String appCode, ULong userId) {
		return this.securityService.getAppByCode(appCode)
				.map(App::getId)
				.map(appId -> Tuples.of(ULongUtil.valueOf(appId), userId));
	}
}
