package com.fincity.saas.commons.core.service.notification;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.common.notification.Notification;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.common.notification.NotificationChannelType;
import com.fincity.saas.commons.core.enums.common.notification.NotificationType;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CoreNotificationService extends AbstractOverridableDataService<Notification, NotificationRepository> {

    private static final String CHANNEL_CONNECTIONS = "channelConnections";

    private final ConnectionService connectionService;

    @Autowired(required = false) //NOSONAR
    @Qualifier("pubRedisAsyncCommand") private RedisPubSubAsyncCommands<String, String> pubAsyncCommand;

    @Value("${redis.notification.eviction.channel:notificationChannel}")
    private String channel;

    protected CoreNotificationService(ConnectionService connectionService) {
        super(Notification.class);
        this.connectionService = connectionService;
    }

    @Override
    protected Mono<Notification> updatableEntity(Notification entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setNotificationType(entity.getNotificationType());
                    existing.setConnectionName(entity.getConnectionName());
                    existing.updateChannelDetails(entity.getChannelDetails());

                    existing.setVersion(existing.getVersion() + 1);

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
    }

    @Override
    public Mono<Notification> create(Notification entity) {

        entity.setName(UniqueUtil.uniqueName(32, entity.getAppCode(), entity.getName()));

        return FlatMapUtil.flatMapMono(() -> this.validate(entity), super::create)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.create"));
    }

    public Mono<Notification> validate(Notification entity) {

        if (entity.getNotificationType() == null || !NotificationType.isLiteralValid(entity.getNotificationType()))
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, "Invalid notification type give: $"),
                    entity.getNotificationType());

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.just(entity),
                        (ca, vEntity) -> this.validateConnections(
                                vEntity.getAppCode(), vEntity.getClientCode(), vEntity.getConnectionName()),
                        (ca, vEntity, validConnections) -> this.validateChannelDetails(vEntity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
    }

    @SuppressWarnings("unchecked")
    private Mono<Boolean> validateConnections(String appCode, String clientCode, String connectionName) {

        return FlatMapUtil.flatMapMono(
                () -> this.connectionService.read(connectionName, appCode, clientCode, ConnectionType.NOTIFICATION),
                connection -> {
                    if (connection.getConnectionDetails() == null
                            || !connection.getConnectionDetails().containsKey(CHANNEL_CONNECTIONS))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                CoreMessageResourceService.CONNECTION_NOT_AVAILABLE,
                                connectionName);

                    return Mono.just((Map<String, String>)
                            connection.getConnectionDetails().get(CHANNEL_CONNECTIONS));
                },
                (connection, channelConnections) -> Flux.fromIterable(channelConnections.values())
                        .flatMap(cConnection -> this.connectionService
                                .hasConnection(cConnection, appCode, clientCode, ConnectionType.NOTIFICATION)
                                .flatMap(hasConnection -> {
                                    if (Boolean.FALSE.equals(hasConnection))
                                        return this.messageResourceService.throwMessage(
                                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                CoreMessageResourceService.CONNECTION_NOT_AVAILABLE,
                                                cConnection);
                                    return Mono.just(Boolean.TRUE);
                                }))
                        .then(Mono.just(Boolean.TRUE)));
    }

    private Mono<Notification> validateChannelDetails(Notification entity) {
        for (Map.Entry<String, Notification.NotificationTemplate> entry :
                entity.getChannelDetails().entrySet()) {

            if (NotificationChannelType.EMAIL.getLiteral().equals(entry.getKey())
                    && !entry.getValue().isValidForEmail())
                return this.messageResourceService.throwMessage(
                        msg -> new GenericException(
                                HttpStatus.BAD_REQUEST, "Please provide delivery details for email"),
                        entity.getNotificationType());

            Notification.DeliveryOptions options = entry.getValue().getDeliveryOptions();

            if (options != null && !options.isValid())
                return this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, "Invalid cron expression '$' given: $"),
                        options.getCronStatement(),
                        entity.getNotificationType());

            if (options == null) entry.getValue().setDeliveryOptions(new Notification.DeliveryOptions());
        }

        return Mono.just(entity);
    }

    @Override
    public Mono<Notification> update(Notification entity) {
        return FlatMapUtil.flatMapMono(
                        () -> this.validate(entity), super::update, (validated, updated) -> this.evictNotificationCache(
                                        updated)
                                .map(x -> updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.update"));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return FlatMapUtil.flatMapMono(
                        () -> super.read(id),
                        notification -> super.delete(id),
                        (notification, deleted) ->
                                this.evictNotificationCache(notification).map(evicted -> deleted))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.delete"));
    }

    public Mono<Notification> readInternalNotification(String name, String appCode, String clientCode) {
        return super.readInternal(name, appCode, clientCode)
                .map(ObjectWithUniqueID::getObject)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.getNotification"));
    }

    private Mono<Boolean> evictNotificationCache(Notification notification) {
        return FlatMapUtil.flatMapMono(
                () -> this.cacheService.evictAll(
                        super.getOutsideServerCacheName(notification.getAppCode(), notification.getName())),
                evicted -> Mono.fromCompletionStage(pubAsyncCommand.publish(
                                this.channel,
                                "Notification : %s, AppCode : %s, ClientCode : %s"
                                        .formatted(
                                                notification.getName(),
                                                notification.getAppCode(),
                                                notification.getClientCode())))
                        .map(x -> evicted));
    }
}
