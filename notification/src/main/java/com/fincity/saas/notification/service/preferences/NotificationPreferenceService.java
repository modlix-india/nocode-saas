package com.fincity.saas.notification.service.preferences;

import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.dao.preference.NotificationPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public abstract class NotificationPreferenceService<R extends UpdatableRecord<R>, D extends NotificationPreference<D>,
		O extends NotificationPreferenceDao<R, D>> extends AbstractJOOQUpdatableDataService<R, ULong, D, O> {

	protected final NotificationMessageResourceService messageResourceService;
	private final CacheService cacheService;

	@Getter
	private IFeignSecurityService securityService;

	protected NotificationPreferenceService(NotificationMessageResourceService messageResourceService,
	                                        CacheService cacheService) {
		this.messageResourceService = messageResourceService;
		this.cacheService = cacheService;
	}

	@Autowired
	public void setSecurityService(IFeignSecurityService securityService) {
		this.securityService = securityService;
	}

	protected abstract String getPreferenceName();

	public abstract String getPreferenceCacheName();

	public abstract boolean isAppLevel();

	private String getCacheKeys(ULong appId, ULong identifierId, NotificationType notificationType) {
		return appId + ":" + identifierId + ":" + notificationType.getLiteral();
	}

	private Mono<D> notificationTypeIdError() {
		return messageResourceService.throwMessage(
				msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				NotificationMessageResourceService.NOTIFICATION_TYPE_NOT_FOUND);
	}

	@Override
	public Mono<D> create(D entity) {

		if (entity.getNotificationTypeId() == null)
			return this.notificationTypeIdError();

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.updateIdentifiers(ca, entity),

						(ca, uEntity) -> this.canUpdatePreference(ca).flatMap(BooleanUtil::safeValueOfWithEmpty),

						(ca, uEntity, canCreate) -> super.create(uEntity),

						(ca, uEntity, canCreate, created) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(created.getAppId(), created.getIdentifierId(), created.getNotificationTypeId())).map(evicted -> created))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_CREATE, getPreferenceName()));
	}

	@Override
	public Mono<D> read(ULong id) {
		return super.read(id);
	}

	@Override
	public Mono<Integer> delete(ULong id) {
		return super.delete(id);
	}

	@Override
	public Mono<D> update(ULong key, Map<String, Object> fields) {
		return super.update(key, fields);
	}

	@Override
	public Mono<D> update(D entity) {
		return super.update(entity);
	}

	private Mono<D> updateIdentifiers(ContextAuthentication ca, D entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.securityService.getAppByCode(ca.getUrlAppCode()).map(app -> ULongUtil.valueOf(app.getId())),

				appId -> {

					if (entity.getAppId() == null)
						entity.setAppId(appId);

					if (entity.getIdentifierId() == null)
						entity.setIdentifierId(this.getIdentifierId(ca));

					return Mono.just(entity);
				},
				(appId, uEntity) -> this.getNotificationPreferenceInternal(uEntity.getAppId(),
						uEntity.getIdentifierId(), uEntity.getNotificationTypeId()),

				(appId, uEntity, notificationPreference) -> {

					if (notificationPreference != null)
						return messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								NotificationMessageResourceService.FORBIDDEN_CREATE, getPreferenceName());

					return Mono.just(uEntity);
				});
	}

	private Mono<Boolean> canUpdatePreference(ContextAuthentication ca) {

		return FlatMapUtil.flatMapMono(

						() -> this.securityService.getAppByCode(ca.getUrlAppCode()),

						app -> FlatMapUtil.flatMapMonoConsolidate(
								() -> this.securityService.isBeingManagedById(ca.getLoggedInFromClientId(), app.getClientId()),
								isManaged -> this.securityService.hasWriteAccess(ca.getUrlAppCode(), ca.getLoggedInFromClientCode()),
								(isManaged, hasEditAccess) -> Mono.just(ca.isSystemClient())),

						(app, managedOrEdit) -> Mono.just(
								managedOrEdit.getT1() || managedOrEdit.getT2() || managedOrEdit.getT3()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationPreferenceService.canUpdatePreference"))
				.switchIfEmpty(Mono.just(Boolean.FALSE));
	}

	public Mono<D> getNotificationPreference(String appCode, ULong identifierId, NotificationType notificationTypeId) {
		return this.securityService.getAppByCode(appCode)
				.flatMap(app -> this.getNotificationPreferenceInternal(ULongUtil.valueOf(app.getId()), identifierId, notificationTypeId));
	}

	private ULong getIdentifierId(ContextAuthentication ca) {
		return ULongUtil.valueOf(this.isAppLevel() ? ca.getLoggedInFromClientId() : ca.getUser().getClientId());
	}

	private Mono<D> getNotificationPreferenceInternal(ULong appId, ULong identifierId, NotificationType notificationTypeId) {
		return this.cacheService.cacheValueOrGet(this.getPreferenceCacheName(),
				() -> this.dao.getNotificationPreference(appId, identifierId, notificationTypeId),
				this.getCacheKeys(appId, identifierId, notificationTypeId)
		);

	}
}
