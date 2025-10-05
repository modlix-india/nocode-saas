package com.fincity.saas.commons.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.repository.ConnectionRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Map;

@Service
public class ConnectionService extends AbstractOverridableDataService<Connection, ConnectionRepository> {


    protected ConnectionService() {
        super(Connection.class);
    }

    @Override
    public Mono<Connection> read(String id) {
        return super.read(id)
                .flatMap(e -> FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, ca -> {
                    if (ca.getClientCode().equals(e.getClientCode()))
                        return Mono.just(e);

                    return this.messageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                            AbstractMongoMessageResourceService.OBJECT_NOT_FOUND,
                            this.getObjectName(),
                            id);
                }));
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
                        conn -> Mono.<Connection>justOrEmpty(conn.getConnectionType() == type ? conn : null),
                        (conn, typedConn) -> Mono.<Connection>justOrEmpty(
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

    public Mono<Connection> readInternalConnection(
            String name, String appCode, String clientCode, ConnectionType type) {

        return FlatMapUtil.flatMapMono(
                () -> super.readInternal(name, appCode, clientCode).map(ObjectWithUniqueID::getObject),
                conn -> {
                    if (conn.getConnectionType() == type)
                        return Mono.<Connection>justOrEmpty(conn);
                    return Mono.<Connection>empty();
                },
                (conn, typedConn) -> {
                    if (typedConn.getClientCode().equals(clientCode)
                            || BooleanUtil.safeValueOf(typedConn.getIsAppLevel()))
                        return Mono.<Connection>justOrEmpty(typedConn);
                    return Mono.<Connection>empty();
                },
                (conn, typedConn, actualConn) -> {
                    if (actualConn.getConnectionType() != ConnectionType.NOTIFICATION) return Mono.just(actualConn);

                    final Connection copyConn = new Connection(actualConn);
                    if (copyConn.getConnectionDetails() == null || copyConn.getConnectionDetails().isEmpty())
                        return Mono.just(actualConn);

                    return Flux.fromIterable(copyConn.getConnectionDetails().entrySet())
                            .flatMap(entry -> StringUtil.safeIsBlank(entry.getValue()) ? Mono.just(Tuples.<String, Object>of(entry.getKey(), ""))
                                    : this.readInternal(entry.getValue().toString(), appCode, clientCode).map(ObjectWithUniqueID::getObject)
                                    .map(e -> Tuples.<String, Object>of(entry.getKey(), e)))
                            .collectMap(Tuple2::getT1, Tuple2::getT2).map(copyConn::setConnectionDetails);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ConnectionService.readInternalConnection"));
    }

    public Mono<Map<String, Connection>> getNotificationConnections(String connectionName, String appCode, String clientCode, ULong triggerUserId, String urlClientCode) {
        return Mono.empty();
//        return FlatMapUtil.flatMapMono(
//                        SecurityContextUtil::getUsersContextAuthentication,
//                        auth -> Mono.empty()
//                )
//                .contextWrite(ReactiveSecurityContextHolder
//                        .withSecurityContext(Mono.just(new SecurityContextImpl(newCA))));
    }
}
