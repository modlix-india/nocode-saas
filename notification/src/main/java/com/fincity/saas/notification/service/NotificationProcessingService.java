package com.fincity.saas.notification.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.document.common.core.Notification;
import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.exception.TemplateProcessingException;
import com.fincity.saas.notification.feign.IFeignCoreService;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.channel.EmailMessage;
import com.fincity.saas.notification.model.request.NotificationChannel;
import com.fincity.saas.notification.model.request.NotificationChannel.NotificationChannelBuilder;
import com.fincity.saas.notification.model.request.NotificationRequest;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.model.response.NotificationResponse;
import com.fincity.saas.notification.model.response.SendResponse;
import com.fincity.saas.notification.model.template.NotificationTemplate;
import com.fincity.saas.notification.mq.NotificationMessageProducer;
import com.fincity.saas.notification.service.template.NotificationTemplateProcessor;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class NotificationProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessingService.class);

    private static final String NOTIFICATION_INFO_CACHE = "notificationInfo";

    private static final String NOTIFICATION = "Notification";

    private final IFeignCoreService coreService;
    private final NotificationMessageResourceService messageResourceService;

    private final UserPreferenceService userPreferenceService;
    private final NotificationConnectionService connectionService;
    private final NotificationTemplateProcessor notificationTemplateProcessor;
    private final NotificationMessageProducer notificationProducer;
    private final SentNotificationService sentNotificationService;

    @Getter
    private CacheService cacheService;

    public NotificationProcessingService(
            IFeignCoreService coreService,
            NotificationMessageResourceService messageResourceService,
            UserPreferenceService userPreferenceService,
            NotificationConnectionService connectionService,
            NotificationTemplateProcessor notificationTemplateProcessor,
            NotificationMessageProducer notificationProducer,
            SentNotificationService sentNotificationService) {
        this.coreService = coreService;
        this.messageResourceService = messageResourceService;
        this.userPreferenceService = userPreferenceService;
        this.connectionService = connectionService;
        this.notificationTemplateProcessor = notificationTemplateProcessor;
        this.notificationProducer = notificationProducer;
        this.sentNotificationService = sentNotificationService;
    }

    @Autowired
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public String getCacheName() {
        return NOTIFICATION_INFO_CACHE;
    }

    public Mono<Boolean> evictChannelEntities(Map<String, String> templates) {
        Map<NotificationChannelType, String> resultMap = NotificationChannelType.getChannelTypeMap(templates);
        return notificationTemplateProcessor.evictTemplateCache(resultMap);
    }

    public Mono<NotificationResponse> processAndSendNotification(NotificationRequest notificationRequest) {
        return this.processNotification(notificationRequest).flatMap(this::sendNotification);
    }

    public Mono<NotificationResponse> sendNotification(SendRequest request) {

        if (request.isEmpty()) {
            logger.info("Received Empty Notification Request: {} [Ignoring]", request);
            return sentNotificationService.toErrorNotification(request).map(NotificationResponse::ofError);
        }

        logger.info("Sending notification request {}", request);

        SendResponse<EmailMessage> response = SendResponse.of(request, NotificationChannelType.EMAIL);

        logger.info("Notification response: {}", response);

        return sentNotificationService
                .toPlatformNotification(request)
                .flatMap(pRequest -> notificationProducer.broadcast(request))
                .map(NotificationResponse::ofSuccess)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationProcessingService.sendNotification"));
    }

    public Mono<SendRequest> processNotification(NotificationRequest notificationRequest) {

        if (notificationRequest == null)
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    NotificationMessageResourceService.UKNOWN_ERROR,
                    NOTIFICATION);

        logger.info("Processing notification request {}", notificationRequest);

        return this.processNotificationInternal(
                notificationRequest.getAppCode(),
                notificationRequest.getClientCode(),
                notificationRequest.getUserId(),
                notificationRequest.getNotificationName(),
                notificationRequest.getChannelObjectMap());
    }

    private Mono<SendRequest> processNotificationInternal(
            String appCode,
            String clientCode,
            BigInteger userId,
            String notificationName,
            Map<String, Object> objectMap) {

        return FlatMapUtil.flatMapMonoWithNull(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.getAppClientUserEntity(ca, appCode, clientCode, userId),
                        (ca, userEntity) -> this.getNotificationInfo(
                                userEntity.getT1(), ca.getUrlClientCode(), userEntity.getT2(), notificationName),
                        (ca, userEntity, notiInfo) ->
                                this.userPreferenceService.getUserPreference(userEntity.getT1(), userEntity.getT3()),
                        (ca, userEntity, notiInfo, userPref) -> this.applyUserPreferences(userPref, notiInfo),
                        (ca, userEntity, notiInfo, userPref, channelDetails) ->
                                this.connectionService.getNotificationConnections(
                                        appCode, clientCode, notiInfo.getChannelConnectionMap()),
                        (ca, userEntity, notiInfo, userPref, channelDetails, connInfo) -> {
                            if (connInfo == null || connInfo.isEmpty())
                                return Mono.just(SendRequest.of(
                                        userEntity.getT1(),
                                        userEntity.getT2(),
                                        userEntity.getT3().toBigInteger(),
                                        notiInfo.getNotificationType()));

                            Map<NotificationChannelType, NotificationTemplate> toSend =
                                    new EnumMap<>(NotificationChannelType.class);

                            connInfo.forEach((channelType, connection) -> {
                                if (channelDetails.containsKey(channelType))
                                    toSend.put(channelType, channelDetails.get(channelType));
                            });

                            return this.createSendRequest(
                                    userEntity.getT1(),
                                    userEntity.getT2(),
                                    userEntity.getT3().toBigInteger(),
                                    notiInfo.getNotificationType(),
                                    userPref,
                                    toSend,
                                    notiInfo.getChannelConnections(),
                                    objectMap);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationProcessingService.processNotification"));
    }

    private Mono<Tuple3<String, String, ULong>> getAppClientUserEntity(
            ContextAuthentication ca, String appCode, String clientCode, BigInteger userId) {
        return Mono.just(Tuples.of(
                StringUtil.safeIsBlank(appCode) ? ca.getUrlAppCode() : appCode,
                StringUtil.safeIsBlank(clientCode) ? ca.getUrlClientCode() : clientCode,
                ULongUtil.valueOf(userId == null ? ca.getUser().getId() : userId)));
    }

    private Mono<Notification> getNotificationInfo(
            String appCode, String urlClientCode, String clientCode, String notificationName) {

        if (StringUtil.safeIsBlank(notificationName))
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AbstractMessageService.OBJECT_NOT_FOUND,
                    NOTIFICATION);

        return this.getNotificationInfoInternal(appCode, urlClientCode, clientCode, notificationName)
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        AbstractMessageService.OBJECT_NOT_FOUND,
                        NOTIFICATION,
                        notificationName));
    }

    private Mono<Notification> getNotificationInfoInternal(
            String appCode, String urlClientCode, String clientCode, String notificationName) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> coreService.getNotificationInfo(urlClientCode, notificationName, appCode, clientCode),
                appCode,
                clientCode,
                notificationName);
    }

    private Mono<Map<NotificationChannelType, NotificationTemplate>> applyUserPreferences(
            UserPreference userPreference, Notification notification) {

        if (userPreference == null) return Mono.just(notification.getChannelDetailMap());

        if (userPreference.hasPreference(notification.getName())) {
            logger.info("User {} dont have preference for {}", userPreference.getUserId(), notification.getName());
            return Mono.just(new EnumMap<>(NotificationChannelType.class));
        }

        return Flux.fromIterable(notification.getChannelDetailMap().entrySet())
                .filter(entry -> userPreference.hasPreference(entry.getKey()))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, () -> new EnumMap<>(NotificationChannelType.class));
    }

    private Mono<SendRequest> createSendRequest(
            String appCode,
            String clientCode,
            BigInteger userId,
            String notificationType,
            UserPreference userPref,
            Map<NotificationChannelType, NotificationTemplate> templateInfoMap,
            Map<String, String> channelConnections,
            Map<String, Object> objectMap) {

        return this.createNotificationChannel(userPref, templateInfoMap, objectMap)
                .map(notificationChannel -> SendRequest.of(
                        appCode, clientCode, userId, notificationType, channelConnections, notificationChannel))
                .onErrorResume(
                        TemplateProcessingException.class,
                        e -> Mono.just(SendRequest.ofError(appCode, clientCode, userId, notificationType, e)));
    }

    private <T extends NotificationMessage<T>> Mono<NotificationChannel> createNotificationChannel(
            UserPreference userPref,
            Map<NotificationChannelType, NotificationTemplate> templateInfoMap,
            Map<String, Object> objectMap) {

        NotificationChannelBuilder notificationChannelBuilder = new NotificationChannelBuilder().preferences(userPref);

        if (templateInfoMap == null || templateInfoMap.isEmpty()) return Mono.just(notificationChannelBuilder.build());

        return FlatMapUtil.flatMapMono(
                () -> Flux.fromIterable(templateInfoMap.entrySet())
                        .<T>flatMap(templateInfo -> this.notificationTemplateProcessor.process(
                                templateInfo.getKey(),
                                templateInfo.getValue(),
                                objectMap.get(templateInfo.getKey().getLiteral())))
                        .collectList(),
                messages -> {
                    messages.forEach(notificationChannelBuilder::addMessage);
                    return Mono.just(notificationChannelBuilder.build());
                });
    }
}
