package com.fincity.saas.commons.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.repository.ConnectionRepository;
import com.fincity.saas.commons.core.service.notification.NotificationProcessingService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ConnectionService extends AbstractOverridableDataService<Connection, ConnectionRepository> {

    @Autowired(required = false)
    @Qualifier("pubRedisAsyncCommand") private RedisPubSubAsyncCommands<String, String> pubAsyncCommand;

    private NotificationProcessingService notificationProcessingService;

    @Value("${redis.connection.eviction.channel:connectionChannel}")
    private String channel;

    protected ConnectionService() {
        super(Connection.class);
    }

    @Lazy
    @Autowired
    private void setNotificationService(NotificationProcessingService notificationProcessingService) {
        this.notificationProcessingService = notificationProcessingService;
    }

    @Override
    public Mono<Connection> read(String id) {
        return super.read(id)
                .flatMap(e -> FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, ca -> {
                    if (ca.getClientCode().equals(e.getClientCode())) return Mono.just(e);

                    return this.messageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                            AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                            this.getObjectName(),
                            id);
                }));
    }

    @Override
    public Mono<Connection> update(Connection entity) {
        return super.update(entity).flatMap(e -> {
            if (pubAsyncCommand == null) return Mono.just(e);

            return Mono.fromCompletionStage(pubAsyncCommand.publish(this.channel, "Connection : " + entity.getId()))
                    .map(x -> e);
        });
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return super.delete(id).flatMap(e -> {
            if (pubAsyncCommand == null) return Mono.just(e);

            return Mono.fromCompletionStage(pubAsyncCommand.publish(this.channel, "Connection : " + id))
                    .map(x -> e);
        });
    }

    @Override
    protected Mono<Connection> updatableEntity(Connection entity) {
        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    existing.setConnectionSubType(entity.getConnectionSubType());
                    existing.setConnectionDetails(entity.getConnectionDetails());
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setIsAppLevel(entity.getIsAppLevel());
                    existing.setOnlyThruKIRun(entity.getOnlyThruKIRun());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConnectionService.updatableEntity"));
    }

    public Mono<Connection> read(String name, String appCode, String clientCode, ConnectionType type) {
        return FlatMapUtil.flatMapMono(
                        () -> this.read(name, appCode, clientCode).map(ObjectWithUniqueID::getObject),
                        conn -> Mono.justOrEmpty(conn.getConnectionType() == type ? conn : null),
                        (conn, typedConn) -> Mono.justOrEmpty(
                                typedConn.getClientCode().equals(clientCode)
                                                || BooleanUtil.safeValueOf(typedConn.getIsAppLevel())
                                        ? typedConn
                                        : null),
                        (conn, typedConn, clientCheckedConn) -> {
                            if (!BooleanUtil.safeValueOf(clientCheckedConn.getOnlyThruKIRun()))
                                return Mono.just(clientCheckedConn);

                            return Mono.deferContextual(cv -> "true".equals(cv.get(DefinitionFunction.CONTEXT_KEY))
                                    ? Mono.just(clientCheckedConn)
                                    : Mono.empty());
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConnectionService.read"));
    }

    public Mono<Boolean> hasConnection(String name, String appCode, String clientCode, ConnectionType type) {
        return this.readInternalConnection(name, appCode, clientCode, type)
                .hasElement()
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<Connection> readInternalConnection(
            String name, String appCode, String clientCode, ConnectionType type) {

        return FlatMapUtil.flatMapMono(
                () -> super.readInternal(name, appCode, clientCode).map(ObjectWithUniqueID::getObject),
                conn -> Mono.justOrEmpty(conn.getConnectionType() == type ? conn : null),
                (conn, typedConn) -> Mono.justOrEmpty(
                        typedConn.getClientCode().equals(clientCode)
                                        || BooleanUtil.safeValueOf(typedConn.getIsAppLevel())
                                ? typedConn
                                : null));
    }
}
