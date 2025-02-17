package com.fincity.saas.notification.service.preferences;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.dao.preferences.UserPrefDao;
import com.fincity.saas.notification.dto.preference.Preference;
import com.fincity.saas.notification.dto.preference.UserPref;
import com.fincity.saas.notification.service.AbstractCodeService;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class UserPrefService<R extends UpdatableRecord<R>, T extends Serializable, D extends UserPref<T, D>,
		O extends UserPrefDao<R, T, D>> extends AbstractCodeService<R, ULong, D, O> {

	protected final NotificationMessageResourceService messageResourceService;
	private final CacheService cacheService;

	@Getter
	private IFeignSecurityService securityService;

	protected UserPrefService(NotificationMessageResourceService messageResourceService,
	                          CacheService cacheService) {
		this.messageResourceService = messageResourceService;
		this.cacheService = cacheService;
	}

	@Autowired
	public void setSecurityService(IFeignSecurityService securityService) {
		this.securityService = securityService;
	}

	protected abstract String getPreferenceName();

	protected abstract String getPreferenceCacheName();

	protected abstract boolean containsOnlyDisable();

	public abstract Mono<D> updateCreationEntity(D entity);

	private String getCacheKeys(ULong appId, ULong userId) {
		return appId + ":" + userId;
	}

	@Override
	public Mono<D> getByCode(String code) {
		return this.dao.getByCode(code);
	}

	@Override
	public Mono<D> update(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.read(key),

						(ca, entity) -> this.canUpdatePreference(ca),

						(ca, entity, canUpdate) -> super.update(key, fields),

						(ca, entity, canUpdate, updated) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(updated.getAppId(), updated.getUserId())).map(evicted -> updated))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getPreferenceName()));
	}

	@Override
	public Mono<D> update(D entity) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.read(entity.getId()),

						(ca, uEntity) -> this.canUpdatePreference(ca),

						(ca, uEntity, canUpdate) -> this.updateCreationEntity(entity).flatMap(super::update),

						(ca, uEntity, canUpdate, updated) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(updated.getAppId(), updated.getUserId())).map(evicted -> updated))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getPreferenceName()));
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {

		return this.read(entity.getId())
				.map(e -> {
					if (this.containsOnlyDisable())
						e.setEnabled(Boolean.FALSE);
					e.setValue(entity.getValue());
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
	public Mono<D> create(D entity) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.updateIdentifiers(ca, entity).flatMap(this::updateCreationEntity),

						(ca, uEntity) -> this.canUpdatePreference(ca).flatMap(BooleanUtil::safeValueOfWithEmpty),

						(ca, uEntity, canCreate) -> this.updateCreationEntity(entity).flatMap(super::create),

						(ca, uEntity, canCreate, created) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(created.getAppId(), created.getUserId())).map(evicted -> created))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_CREATE, getPreferenceName()));
	}

	@Override
	public Mono<D> read(ULong id) {

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

						(ca, entity, canUpdate, deleted) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(entity.getAppId(), entity.getUserId())).map(evicted -> deleted))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getPreferenceName()));
	}

	private Mono<D> updateIdentifiers(ContextAuthentication ca, D entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.securityService.getAppByCode(ca.getUrlAppCode()).map(app -> ULongUtil.valueOf(app.getId())),

				appId -> {

					if (entity.getAppId() == null)
						entity.setAppId(appId);

					if (entity.getUserId() == null)
						entity.setUserId(ULongUtil.valueOf(ca.getUser().getId()));

					return Mono.just(entity);
				},
				(appId, uEntity) -> this.getNotificationPreferenceInternal(uEntity.getAppId(),
						uEntity.getUserId()),

				(appId, uEntity, notificationPreference) -> {

					if (notificationPreference != null && notificationPreference.hasPreference(uEntity.getValue()))
						return messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								NotificationMessageResourceService.FORBIDDEN_CREATE, getPreferenceName());

					return Mono.just(uEntity);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationPreferenceService.UserPrefService"));
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
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationPreferenceService.UserPrefService"))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	public Mono<Preference<T>> getNotificationPreference(ContextAuthentication ca) {
		return this.getNotificationPreference(ca.getUrlAppCode(), ULongUtil.valueOf(ca.getUser().getId()));
	}

	public Mono<Preference<T>> getNotificationPreference(String appCode, ULong userId) {
		return this.securityService.getAppByCode(appCode)
				.flatMap(app -> this.getNotificationPreferenceInternal(ULongUtil.valueOf(app.getId()), userId));
	}

	private Mono<Preference<T>> getNotificationPreferenceInternal(ULong appId, ULong userId) {
		return this.cacheService.cacheValueOrGet(this.getPreferenceCacheName(),
				() -> this.getPreference(this.dao.getUserPreferences(appId, userId)),
				this.getCacheKeys(appId, userId));
	}

	private Mono<Preference<T>> getPreference(Flux<D> entities) {
		return entities.reduce(new Preference<>(),
				(preference, entity) -> {
					if (preference.getAppId() == null) {
						preference.setAppId(entity.getAppId());
						preference.setUserId(entity.getUserId());
						preference.setContainsOnlyDisable(this.containsOnlyDisable());
					}
					preference.addCode(entity.getCode());
					if (this.containsOnlyDisable())
						preference.addDisabledPreference(entity.getValue());
					else if (entity.isEnabled())
						preference.addPreference(entity.getValue());
					else
						preference.addDisabledPreference(entity.getValue());
					return preference;
				});
	}
}
