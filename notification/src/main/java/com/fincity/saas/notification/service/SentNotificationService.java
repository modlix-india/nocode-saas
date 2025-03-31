package com.fincity.saas.notification.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.dao.SentNotificationDao;
import com.fincity.saas.notification.dto.SentNotification;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.jooq.tables.records.NotificationSentNotificationsRecord;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.service.channel.IChannelStorageService;
import com.fincity.saas.notification.service.channel.inapp.InAppNotificationService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Service
public class SentNotificationService
		extends AbstractCodeService<NotificationSentNotificationsRecord, ULong, SentNotification, SentNotificationDao> {

	private static final String SENT_NOTIFICATION_CACHE = "sentNotification";

	private final InAppNotificationService inAppNotificationService;

	private final Map<NotificationChannelType, IChannelStorageService<ULong, ? extends AbstractUpdatableDTO<ULong, ULong>, ?>> storageServices = new EnumMap<>(
			NotificationChannelType.class);

	private CacheService cacheService;

	public SentNotificationService(InAppNotificationService inAppNotificationService) {
		this.inAppNotificationService = inAppNotificationService;
	}

	@PostConstruct
	public void init() {
		this.storageServices.put(inAppNotificationService.getChannelType(), inAppNotificationService);
	}

	@Override
	protected String getCacheName() {
		return SENT_NOTIFICATION_CACHE;
	}

	@Override
	protected CacheService getCacheService() {
		return cacheService;
	}

	@Autowired
	private void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	@Override
	protected Mono<ULong> getLoggedInUserId() {
		return FlatMapUtil.flatMapMono(
				SecurityContextUtil::getUsersContextAuthentication,
				ca -> Mono.justOrEmpty(ca.isAuthenticated() ? ULong.valueOf(ca.getUser().getId()) : null));
	}

	@Override
	protected Mono<SentNotification> updatableEntity(SentNotification entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setNotificationStage(entity.getNotificationStage());
					e.setEmail(entity.isEmail());
					e.setEmailDeliveryStatus(entity.getEmailDeliveryStatus());
					e.setInApp(entity.isInApp());
					e.setInAppDeliveryStatus(entity.getInAppDeliveryStatus());
					e.setMobilePush(entity.isMobilePush());
					e.setMobilePushDeliveryStatus(entity.getMobilePushDeliveryStatus());
					e.setWebPush(entity.isWebPush());
					e.setWebPushDeliveryStatus(entity.getWebPushDeliveryStatus());
					e.setSms(entity.isSms());
					e.setSmsDeliveryStatus(entity.getSmsDeliveryStatus());
					e.setError(entity.isError());
					e.setErrorInfo(entity.getErrorInfo());
					e.setChannelErrors(entity.getChannelErrors());
					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.remove("id");
		fields.remove(SentNotification.Fields.code);
		fields.remove(SentNotification.Fields.clientCode);
		fields.remove(SentNotification.Fields.appCode);
		fields.remove(SentNotification.Fields.userId);
		fields.remove(SentNotification.Fields.notificationType);
		fields.remove(SentNotification.Fields.notificationChannel);
		fields.remove(SentNotification.Fields.triggerTime);
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}

	public Mono<SendRequest> toPlatformNotification(SendRequest request) {
		return this.createFromRequest(request, NotificationStage.PLATFORM, NotificationDeliveryStatus.CREATED)
				.flatMap(entity -> Mono.just(request))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.toPlatformNotification"));
	}

	public Mono<SendRequest> toPlatformNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.createFromRequest(request, NotificationStage.PLATFORM, deliveryStatus, channelTypes)
				.flatMap(entity -> Mono.just(request))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"SentNotificationService.toPlatformNotification[NotificationChannelType]"));
	}

	public Mono<SendRequest> toGatewayNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus) {
		return this.updateOrCreateFromRequest(request, NotificationStage.GATEWAY, deliveryStatus)
				.flatMap(entity -> Mono.just(request))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.toGatewayNotification"));
	}

	public Mono<SendRequest> toGatewayNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateOrCreateFromRequest(request, NotificationStage.GATEWAY, deliveryStatus, channelTypes)
				.flatMap(entity -> Mono.just(request))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.toGatewayNotification"));
	}

	public Mono<SendRequest> toNetworkNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateOrCreateFromRequest(request, NotificationStage.NETWORK, deliveryStatus, channelTypes)
				.flatMap(entity -> Mono.just(request))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.toNetworkNotification"));
	}

	public Mono<SendRequest> toErrorNotification(SendRequest request) {
		return this.updateOrCreateFromRequest(request, null, NotificationDeliveryStatus.ERROR)
				.flatMap(entity -> Mono.just(request))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.toErrorNotification"));
	}

	private Mono<SentNotification> createFromRequest(SendRequest request, NotificationStage notificationStage,
			NotificationDeliveryStatus deliveryStatus, NotificationChannelType... channelTypes) {
		LocalDateTime triggerTime = LocalDateTime.now();

		return FlatMapUtil.flatMapMono(

				() -> this.createSentNotification(request, triggerTime, notificationStage, deliveryStatus,
						channelTypes),

				this::create,

				(sendNotification, created) -> this.createChannelStorage(request, triggerTime, notificationStage,
						deliveryStatus),

				(sendNotification, created, sCreated) -> Mono.just(created))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.createFromRequest"));

	}

	private Mono<SentNotification> updateOrCreateFromRequest(SendRequest request, NotificationStage notificationStage,
			NotificationDeliveryStatus deliveryStatus, NotificationChannelType... channelTypes) {
		LocalDateTime triggerTime = LocalDateTime.now();

		return FlatMapUtil.flatMapMono(

				() -> Mono.just(request),

				req -> super.getByCode(req.getCode()),

				(req, sentNotification) -> this.updateSentNotification(sentNotification, req, triggerTime,
						notificationStage, deliveryStatus, channelTypes),

				(req, sentNotification, updatedNotification) -> this.update(updatedNotification),

				(req, sentNotification, updatedNotification, updated) -> this.updateChannelStorage(request, triggerTime,
						notificationStage, deliveryStatus),

				(req, sentNotification, updatedNotification, updated, sUpdated) -> this.evictCode(updated.getCode())
						.map(evictedCode -> updated))
				.switchIfEmpty(this.createFromRequest(request, notificationStage, deliveryStatus, channelTypes))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SentNotificationService.updateFromRequest"));
	}

	private Mono<SentNotification> createSentNotification(SendRequest request, LocalDateTime createdTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateStageAndStatus(SentNotification.from(request, createdTime), request, createdTime,
				notificationStage, deliveryStatus, channelTypes);
	}

	private Mono<SentNotification> updateSentNotification(SentNotification sentNotification, SendRequest request,
			LocalDateTime createdTime, NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateStageAndStatus(sentNotification, request, createdTime, notificationStage, deliveryStatus,
				channelTypes);
	}

	private Mono<SentNotification> updateStageAndStatus(SentNotification sentNotification, SendRequest request,
			LocalDateTime createdTime, NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {

		if (notificationStage != null)
			sentNotification.setNotificationStage(notificationStage);

		if (deliveryStatus == null || request.isEmpty())
			return Mono.just(sentNotification);

		this.updateErrorStatus(sentNotification, request, deliveryStatus);

		List<NotificationChannelType> enabledChannels = request.getChannels().getEnabledChannels();

		List<NotificationChannelType> channels = channelTypes.length > 0
				? Arrays.stream(channelTypes).filter(enabledChannels::contains).toList()
				: enabledChannels;

		Map<String, LocalDateTime> deliveryStatusInfo = Map.of(deliveryStatus.getLiteral(), createdTime);

		channels.forEach(ct -> sentNotification.updateChannelInfo(ct, Boolean.TRUE, deliveryStatusInfo, Boolean.TRUE));

		return Mono.just(sentNotification);
	}

	private void updateErrorStatus(SentNotification sentNotification, SendRequest request,
			NotificationDeliveryStatus deliveryStatus) {

		if (deliveryStatus.isError()) {
			if (request.getErrorInfo() != null)
				sentNotification.setErrorInfo(request.getErrorInfo());

			if (request.getChannelErrors() != null && !request.getChannelErrors().isEmpty())
				sentNotification.setChannelErrorInfo(request.getChannelErrors());
		}
	}

	private Mono<Boolean> createChannelStorage(SendRequest request, LocalDateTime triggerTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {

		return Flux.fromIterable(storageServices.entrySet())
				.parallel()
				.runOn(Schedulers.boundedElastic())
				.filter(entry -> request.getChannels().containsChannel(entry.getKey()))
				.flatMap(service -> service.getValue()
						.saveChannelNotification(request, triggerTime, notificationStage, deliveryStatus)
						.map(c -> Boolean.TRUE))
				.sequential()
				.any(Boolean.TRUE::equals);
	}

	private Mono<Boolean> updateChannelStorage(SendRequest request, LocalDateTime triggerTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {

		return Flux.fromIterable(storageServices.entrySet())
				.parallel()
				.runOn(Schedulers.boundedElastic())
				.filter(entry -> request.getChannels().containsChannel(entry.getKey()))
				.flatMap(service -> service.getValue()
						.updateChannelNotification(request, triggerTime, notificationStage, deliveryStatus)
						.map(c -> Boolean.TRUE))
				.sequential()
				.any(Boolean.TRUE::equals);
	}
}
