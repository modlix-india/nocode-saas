package com.fincity.saas.notification.service.preferences;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
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
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.dao.preference.NotificationPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

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

	private String getCacheKeys(ULong appId, ULong identifierId) {
		return appId + ":" + identifierId;
	}

	@Override
	public Mono<D> create(D entity) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.updateIdentifiers(ca, entity),

						(ca, uEntity) -> this.canUpdatePreference(ca).flatMap(BooleanUtil::safeValueOfWithEmpty),

						(ca, uEntity, canCreate) -> super.create(uEntity),

						(ca, uEntity, canCreate, created) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(created.getAppId(), created.getIdentifierId())).map(evicted -> created))
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

	public Mono<D> read(String code) {
		if (StringUtil.safeIsBlank(code))
			return Mono.empty();

		return this.securityService.getAppByCode(code)
				.flatMap(app -> this.read(ULongUtil.valueOf(app.getId())))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationPreferenceService.read[code]"));
	}

	@Override
	public Mono<Integer> delete(ULong id) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> this.read(id),

						(ca, entity) -> this.canUpdatePreference(ca),

						(ca, entity, canDelete) -> super.delete(id),

						(ca, entity, canUpdate, deleted) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(entity.getAppId(), entity.getIdentifierId())).map(evicted -> deleted))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getPreferenceName()));
	}

	@Override
	public Mono<D> update(ULong key, Map<String, Object> fields) {

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> key != null ? this.read(key) :
								this.getNotificationPreference(ca.getUrlAppCode(), this.getIdentifierId(ca)),

						(ca, entity) -> this.canUpdatePreference(ca),

						(ca, entity, canUpdate) -> super.update(key, fields),

						(ca, entity, canUpdate, updated) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(updated.getAppId(), updated.getIdentifierId())).map(evicted -> updated))
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

						(ca, uEntity, canUpdate) -> super.update(entity),

						(ca, uEntity, canUpdate, updated) -> cacheService.evict(this.getPreferenceCacheName(),
								this.getCacheKeys(updated.getAppId(), updated.getIdentifierId())).map(evicted -> updated))
				.switchIfEmpty(messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
						NotificationMessageResourceService.FORBIDDEN_UPDATE, this.getPreferenceName()));
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setPreferences(entity.getPreferences());
					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.keySet().retainAll(List.of("preferences"));

		return Mono.just(fields);
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
						uEntity.getIdentifierId()),

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

	public Mono<D> getNotificationPreference(BigInteger identifierId, boolean current) {

		boolean getCurrent = identifierId == null || current;

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> getCurrent ? Mono.just(this.getIdentifierId(ca)) : Mono.just(ULongUtil.valueOf(identifierId)),

						(ca, id) -> this.getNotificationPreference(ca.getUrlAppCode(), id))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationPreferenceService.getNotificationPreference[identifierId, current]"));
	}

	public Mono<D> getNotificationPreference(String clientCode, boolean current) {

		boolean getCurrent = StringUtil.safeIsBlank(clientCode) || current;

		if (!isAppLevel())
			return Mono.empty();

		return FlatMapUtil.flatMapMono(

						SecurityContextUtil::getUsersContextAuthentication,

						ca -> getCurrent ? Mono.justOrEmpty(this.getIdentifierCode(ca)) : Mono.just(clientCode),

						(ca, id) -> this.getNotificationPreference(ca.getUrlAppCode(), id))
				.switchIfEmpty(Mono.empty())
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationPreferenceService.getNotificationPreference[clientCode, current]"));
	}

	public Mono<D> getNotificationPreference(String appCode, String clientCode) {
		return this.getClientAndAppId(clientCode, appCode)
				.flatMap(clientAppIds -> this.getNotificationPreferenceInternal(clientAppIds.getT2(), clientAppIds.getT1()));
	}

	public Mono<D> getNotificationPreference(String appCode, ULong identifierId) {
		return this.securityService.getAppByCode(appCode)
				.flatMap(app -> this.getNotificationPreferenceInternal(ULongUtil.valueOf(app.getId()), identifierId));
	}

	public Mono<D> getNotificationPreference(String code) {
		return this.getNotificationPreferenceInternal(code);
	}

	private Mono<D> getNotificationPreferenceInternal(String code) {
		return this.cacheService.cacheValueOrGet(this.getPreferenceCacheName(),
				() -> this.dao.getByCode(code), code);
	}

	private Mono<D> getNotificationPreferenceInternal(ULong appId, ULong identifierId) {
		return this.cacheService.cacheValueOrGet(this.getPreferenceCacheName(),
				() -> this.dao.getNotificationPreference(appId, identifierId),
				this.getCacheKeys(appId, identifierId)
		);
	}

	private ULong getIdentifierId(ContextAuthentication ca) {
		return ULongUtil.valueOf(this.isAppLevel() ? ca.getLoggedInFromClientId() : ca.getUser().getClientId());
	}

	private String getIdentifierCode(ContextAuthentication ca) {
		//TODO : Get client code for user level and process
		return this.isAppLevel() ? ca.getLoggedInFromClientCode() : null;
	}

	private Mono<Tuple2<ULong, ULong>> getClientAndAppId(String clientCode, String appCode) {
		return FlatMapUtil.flatMapMonoConsolidate(
				() -> this.securityService.getClientByCode(clientCode).map(client -> ULongUtil.valueOf(client.getId())),
				clientId -> this.securityService.getAppByCode(appCode).map(app -> ULongUtil.valueOf(app.getId()))
		);
	}
}
