package com.fincity.saas.commons.core.service.notification;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.feign.IFeignNotificationService;
import com.fincity.saas.commons.core.model.notification.NotificationCacheRequest;
import com.fincity.saas.commons.core.model.notification.NotificationRequest;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import java.math.BigInteger;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CoreNotificationProcessingService {

    private final IFeignNotificationService notificationService;
    private final IFeignSecurityService securityService;

    private CoreMessageResourceService coreMsgService;

    public CoreNotificationProcessingService(
            IFeignNotificationService notificationService, IFeignSecurityService securityService) {
        this.notificationService = notificationService;
        this.securityService = securityService;
    }

    @Autowired
    private void setCoreMsgService(CoreMessageResourceService coreMsgService) {
        this.coreMsgService = coreMsgService;
    }

    public Mono<Boolean> processAndSendNotification(
            String appCode,
            String clientCode,
            BigInteger userId,
            String notificationName,
            Map<String, Object> objectMap) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        (ca, actup) -> securityService
                                .appInheritance(actup.getT1(), ca.getUrlClientCode(), actup.getT2())
                                .map(clientCodes -> Mono.just(clientCodes.contains(actup.getT2())))
                                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                                .switchIfEmpty(coreMsgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        AbstractMongoMessageResourceService.FORBIDDEN_APP_ACCESS)),
                        (ca, actup, hasAppAccess) -> this.securityService
                                .isUserBeingManaged(userId, clientCode)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                                .switchIfEmpty(coreMsgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        CoreMessageResourceService.INVALID_USER_FOR_CLIENT)),
                        (ca, actup, hasAppAccess, hasUserAccess) ->
                                notificationService.sendNotification(new NotificationRequest()
                                        .setAppCode(actup.getT1())
                                        .setClientCode(actup.getT2())
                                        .setUserId(userId)
                                        .setNotificationName(notificationName)
                                        .setChannelObjectMap(objectMap)))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "NotificationProcessingService.processAndSendNotification"));
    }

    public Mono<Boolean> evictNotificationChannelCache(Map<String, String> channelEntities) {
        return this.notificationService.evictNotificationCache(
                new NotificationCacheRequest().setChannelEntities(channelEntities));
    }
}
