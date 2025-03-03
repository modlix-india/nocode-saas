package com.fincity.saas.notification.service;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.document.Notification;
import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.feign.IFeignCoreService;
import com.fincity.saas.notification.model.NotificationChannel;
import com.fincity.saas.notification.model.NotificationChannel.NotificationChannelBuilder;
import com.fincity.saas.notification.model.NotificationRequest;
import com.fincity.saas.notification.model.NotificationTemplate;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.service.template.TemplateProcessor;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class NotificationProcessingService {

	public static final String NOTIFICATION_INFO_CACHE = "notificationInfo";
	private static final Logger logger = LoggerFactory.getLogger(NotificationProcessingService.class);
	private static final String NOTIFICATION_CONN_CACHE = "notificationConn";

	private static final String NOTIFICATION = "Notification";
	private static final String NOTIFICATION_CONNECTION_TYPE = "NOTIFICATION";

	private final IFeignCoreService coreService;
	private final NotificationMessageResourceService messageResourceService;

	private final UserPreferenceService userPreferenceService;

	private final TemplateProcessor templateProcessor;

	@Getter
	private CacheService cacheService;

	public NotificationProcessingService(IFeignCoreService coreService,
			NotificationMessageResourceService messageResourceService, UserPreferenceService userPreferenceService,
			TemplateProcessor templateProcessor) {
		this.coreService = coreService;
		this.messageResourceService = messageResourceService;
		this.userPreferenceService = userPreferenceService;
		this.templateProcessor = templateProcessor;
	}

	@Autowired
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	private String getCacheKeys(String... entityNames) {
		return String.join(":", entityNames);
	}

	private String getNotificationConnCache() {
		return NOTIFICATION_CONN_CACHE;
	}

	private String getNotificationInfoCache() {
		return NOTIFICATION_INFO_CACHE;
	}

	public Mono<Boolean> processAndSendNotification(NotificationRequest notificationRequest) {
		return this.processNotification(notificationRequest)
				.flatMap(this::sendNotification);
	}

	public Mono<Boolean> sendNotification(SendRequest request) {

		logger.info("Sending send notification request {}", request);

		return Mono.just(true)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationProcessingService.sendNotification"));
	}

	public Mono<SendRequest> processNotification(NotificationRequest notificationRequest) {

		if (notificationRequest == null)
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					NotificationMessageResourceService.UKNOWN_ERROR, NOTIFICATION);

		logger.info("Processing notification request {}", notificationRequest);

		return this.processNotificationInternal(notificationRequest.getAppCode(), notificationRequest.getClientCode(),
				notificationRequest.getUserId(), notificationRequest.getNotificationName(),
				notificationRequest.getObjectMap());
	}

	private Mono<SendRequest> processNotificationInternal(String appCode, String clientCode, BigInteger userId,
			String notificationName, Map<String, Object> objectMap) {

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getAppClientUserEntity(ca, appCode, clientCode, userId),

				(ca, userEntity) -> this.getNotificationInfo(userEntity.getT1(), userEntity.getT2(), notificationName),

				(ca, userEntity, notiInfo) -> this.userPreferenceService
						.getUserPreference(userEntity.getT2(), userEntity.getT3()),

				(ca, userEntity, notiInfo, userPref) -> this.applyUserPreferences(userPref, notiInfo),

				(ca, userEntity, notiInfo, userPref, channelDetails) -> this.getNotificationConnections(appCode,
						clientCode, channelDetails),

				(ca, userEntity, notiInfo, userPref, channelDetails, connInfo) -> {

					if (connInfo == null || connInfo.isEmpty())
						return Mono.just(SendRequest.of(userEntity.getT1(), userEntity.getT2(),
								userEntity.getT3().toBigInteger(), notiInfo.getNotificationType()));

					Map<NotificationChannelType, NotificationTemplate> toSend = new EnumMap<>(
							NotificationChannelType.class);

					connInfo.forEach((channelType, connection) -> {
						if (channelDetails.containsKey(channelType))
							toSend.put(channelType, channelDetails.get(channelType));
					});

					return this.createSendRequest(clientCode, appCode, notiInfo.getNotificationType(), userPref, toSend,
							objectMap);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationProcessingService.processNotification"));
	}

	private Mono<Tuple3<String, String, ULong>> getAppClientUserEntity(ContextAuthentication ca, String appCode,
			String clientCode, BigInteger userId) {
		return Mono.just(Tuples.of(
				StringUtil.safeIsBlank(appCode) ? ca.getUrlAppCode() : appCode,
				StringUtil.safeIsBlank(clientCode) ? ca.getUrlClientCode() : clientCode,
				ULongUtil.valueOf(userId == null ? ca.getUser().getId() : userId)));
	}

	private Mono<Notification> getNotificationInfo(String appCode, String clientCode, String notificationName) {

		if (StringUtil.safeIsBlank(notificationName))
			return this.messageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					AbstractMessageService.OBJECT_NOT_FOUND, NOTIFICATION);

		return this.getNotificationInfoInternal(appCode, clientCode, notificationName).switchIfEmpty(
				this.messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						AbstractMessageService.OBJECT_NOT_FOUND, NOTIFICATION, notificationName));
	}

	private Mono<Notification> getNotificationInfoInternal(String appCode, String clientCode, String notificationName) {
		return cacheService.cacheValueOrGet(this.getNotificationInfoCache(),
				() -> coreService.getNotificationInfo(notificationName, appCode, clientCode),
				this.getCacheKeys(appCode, clientCode, notificationName));
	}

	private Mono<Map<NotificationChannelType, NotificationTemplate>> applyUserPreferences(UserPreference userPreference,
			Notification notification) {

		if (userPreference == null)
			return Mono.just(notification.getChannelDetailMap());

		if (!userPreference.hasPreference(notification.getName())) {
			logger.info("User {} dont have preference for {}", userPreference.getUserId(), notification.getName());
			return Mono.just(new EnumMap<>(NotificationChannelType.class));
		}

		Map<NotificationChannelType, NotificationTemplate> channelDetails = notification.getChannelDetailMap()
				.entrySet()
				.stream().filter(entry -> userPreference.hasPreference(entry.getKey()))
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(a, b) -> b,
						() -> new EnumMap<>(NotificationChannelType.class)));

		return Mono.just(channelDetails);
	}

	private Mono<Map<NotificationChannelType, Map<String, Object>>> getNotificationConnections(String appCode,
			String clientCode, Map<NotificationChannelType, NotificationTemplate> channelDetails) {

		if (channelDetails == null || channelDetails.isEmpty())
			return Mono.empty();

		Map<NotificationChannelType, Map<String, Object>> connections = new EnumMap<>(NotificationChannelType.class);

		return Flux.fromIterable(channelDetails.entrySet())
				.flatMap(
						connection -> this
								.getNotificationConn(appCode, clientCode, connection.getValue().getConnectionName())
								.filter(connectionMap -> !(connectionMap == null || connectionMap.isEmpty()))
								.doOnNext(connDetails -> connections.put(connection.getKey(), connDetails)))
				.then(Mono.just(connections));
	}

	private Mono<Map<String, Object>> getNotificationConn(String appCode, String clientCode, String connectionName) {
		return cacheService.cacheValueOrGet(this.getNotificationConnCache(),
				() -> coreService.getConnection(connectionName, appCode, clientCode, NOTIFICATION_CONNECTION_TYPE),
				this.getCacheKeys(appCode, clientCode, connectionName));
	}

	private Mono<SendRequest> createSendRequest(String appCode, String clientCode, String notificationType,
			UserPreference userPref, Map<NotificationChannelType, NotificationTemplate> templateInfoMap,
			Map<String, Object> objectMap) {

		return this.createNotificationChannel(userPref, templateInfoMap, objectMap)
				.map(notificationChannel -> SendRequest.of(clientCode, appCode, userPref.getUserId().toBigInteger(),
						notificationType, notificationChannel));
	}

	private Mono<NotificationChannel> createNotificationChannel(UserPreference userPref,
			Map<NotificationChannelType, NotificationTemplate> templateInfoMap, Map<String, Object> objectMap) {

		NotificationChannelBuilder notificationChannelBuilder = new NotificationChannelBuilder().preferences(userPref);

		return Flux.fromIterable(templateInfoMap.entrySet())
				.flatMap(
						templateInfo -> this.templateProcessor.process(templateInfo.getValue(), objectMap))
				.mapNotNull(notificationChannelBuilder::addMessage)
				.then(Mono.just(notificationChannelBuilder.build()));
	}

}
