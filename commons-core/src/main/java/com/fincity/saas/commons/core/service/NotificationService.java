package com.fincity.saas.commons.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Notification;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.mq.notifications.NotificationQueObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.data.CircularLinkedList;
import com.fincity.saas.commons.util.data.DoublePointerNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.math.BigInteger;
import java.util.Map;

@Service
public class NotificationService extends AbstractOverridableDataService<Notification, NotificationRepository> {

    public static final String USER_ID = "User Id";
    public static final String CLIENT_ID = "Client Id";
    public static final String CLIENT_CODE = "Client Code";

    @Value("${notification.mq.exchange:notifications}")
    private String exchange;
    @Value("${notification.mq.routingkeys:notifications1,notifications2,notifications3}")
    private String routingKey;
    private final AmqpTemplate amqpTemplate;

    private DoublePointerNode<String> nextRoutingKey;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(NotificationService.class);

    protected NotificationService(AmqpTemplate amqpTemplate) {
        super(Notification.class);
        this.amqpTemplate = amqpTemplate;
    }

    @PostConstruct
    protected void init() {

        nextRoutingKey = new CircularLinkedList<>(this.routingKey.split(",")).getHead();
    }

    @Override
    protected Mono<Notification> updatableEntity(Notification entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setNotificationType(entity.getNotificationType());
                    existing.setDefaultLanguage(entity.getDefaultLanguage());
                    existing.setLanguageExpression(entity.getLanguageExpression());
                    existing.setVariableSchema(entity.getVariableSchema());
                    existing.setChannelTemplates(entity.getChannelTemplates());
                    existing.setChannelConnections(entity.getChannelConnections());

                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
    }

    public Mono<Boolean> processAndSendNotification(
            String appCode,
            String clientCode,
            String connectionName,
            BigInteger targetId,
            String targetCode,
            String targetType,
            String filterAuthorization,
            String notificationName,
            Map<String, Object> payload) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),

                        (ca, actualTuple) -> {
                            switch (targetType) {
                                case USER_ID -> {
                                    if (targetId.longValue() <= 0 && !ca.isSystemClient())
                                        return Mono.empty();

                                    if (ca.isSystemClient())
                                        return Mono.just(true);

                                    return this.securityService.isUserBeingManaged(targetId, ca.getClientCode());
                                }
                                case CLIENT_ID -> {
                                    if (targetId.longValue() <= 0 && !ca.isSystemClient())
                                        return Mono.empty();

                                    if (ca.isSystemClient())
                                        return Mono.just(true);

                                    return this.securityService.isBeingManaged(ca.getUser().getClientId(), targetId);
                                }
                                case CLIENT_CODE -> {
                                    if ((targetCode == null || targetCode.isEmpty()) && !ca.isSystemClient())
                                        return Mono.empty();

                                    if (ca.isSystemClient())
                                        return Mono.just(true);

                                    return this.securityService.isBeingManaged(ca.getClientCode(), targetCode);
                                }

                                default -> {
                                    logger.error("Invalid target type ({}) for notification processing with context : {}", targetType, ca);
                                    return Mono.empty();
                                }
                            }
                        },
                        (ca, actualTuple, hasUserAccess) -> {

                            if (!hasUserAccess) return Mono.empty();

                            this.nextRoutingKey = nextRoutingKey.getNext();
                            return Mono.just(new NotificationQueObject()
                                            .setAppCode(actualTuple.getT1())
                                            .setClientCode(actualTuple.getT2())
                                            .setTargetId(targetId)
                                            .setTargetType(targetType)
                                            .setTargetCode(targetCode)
                                            .setClientCode(ca.getUrlClientCode())
                                            .setTriggeredUserId(ca.getUser().getId())
                                            .setFilterAuthorization(filterAuthorization)
                                            .setNotificationName(notificationName)
                                            .setConnectionName(connectionName)
                                            .setPayload(payload))
                                    .flatMap(q -> Mono.deferContextual(cv -> {
                                        if (!cv.hasKey(LogUtil.DEBUG_KEY))
                                            return Mono.just(q);
                                        q.setXDebug(cv.get(LogUtil.DEBUG_KEY)
                                                .toString());
                                        return Mono.just(q);
                                    }))
                                    .flatMap(q -> Mono.fromCallable(() -> {
                                        amqpTemplate.convertAndSend(exchange, nextRoutingKey.getItem(), q);
                                        return true;
                                    }));
                        }
                )
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "NotificationService.processAndSendNotification"));
    }
}