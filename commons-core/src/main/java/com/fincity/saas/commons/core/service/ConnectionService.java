package com.fincity.saas.commons.core.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.model.NotificationConnectionDetails;
import com.fincity.saas.commons.core.repository.ConnectionRepository;
import com.fincity.saas.commons.core.util.OutboundUrlUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ConnectionService extends AbstractOverridableDataService<Connection, ConnectionRepository> {
    /** Draftable, like every other core object. See StorageService for why. */
    @Override
    protected boolean isDraftable() {
        return true;
    }


    protected ConnectionService() {
        super(Connection.class);
    }

    /**
     * Every distinct app-data connection on a given backend, across all apps.
     *
     * Distinct by connection DETAILS rather than by document, because one MySQL
     * server is usually shared by many apps and sweeping it once per app would do
     * the same work several times over. Used by migration recovery, which has to
     * find work without being told which app it belongs to.
     */
    public Flux<Connection> allAppData(ConnectionSubType subType) {

        return this.mongoTemplate
                .find(
                        new org.springframework.data.mongodb.core.query.Query(
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .andOperator(
                                                org.springframework.data.mongodb.core.query.Criteria
                                                        .where("connectionType")
                                                        .is(ConnectionType.APP_DATA),
                                                org.springframework.data.mongodb.core.query.Criteria
                                                        .where("connectionSubType")
                                                        .is(subType))),
                        Connection.class,
                        this.getCollectionName())
                .distinct(c -> c.getConnectionDetails() == null ? "" : c.getConnectionDetails().toString());
    }

    /**
     * Every distinct app-data server ONE APP uses, on a given backend.
     *
     * The app-scoped twin of {@link #allAppData(ConnectionSubType)}. An appData
     * connection is an overridable document like any other, so a client may bring
     * its own database: {@code ConnectionService.read} accepts a connection whose
     * clientCode matches, or an app-level one, which means two clients of the same
     * app can legitimately sit on two different MySQL servers.
     *
     * Anything that reconciles or inspects TABLES has to walk all of them. Reading
     * one connection and sweeping the schemas on its server silently skips every
     * tenant hosted elsewhere - the definition saves, the report comes back empty
     * for them, and their tables keep the old shape until a write hits a column
     * that is not there.
     *
     * Distinct by connection DETAILS, like its sibling, so the common case - every
     * client on the platform's own datasource through {@code useDefaultConnection}
     * - collapses to a single sweep rather than one per client.
     */
    public Flux<Connection> allAppData(ConnectionSubType subType, String appCode) {

        if (StringUtil.safeIsBlank(appCode)) return Flux.empty();

        return this.mongoTemplate
                .find(
                        new org.springframework.data.mongodb.core.query.Query(
                                new org.springframework.data.mongodb.core.query.Criteria()
                                        .andOperator(
                                                org.springframework.data.mongodb.core.query.Criteria
                                                        .where("connectionType")
                                                        .is(ConnectionType.APP_DATA),
                                                org.springframework.data.mongodb.core.query.Criteria
                                                        .where("connectionSubType")
                                                        .is(subType),
                                                org.springframework.data.mongodb.core.query.Criteria
                                                        .where("appCode")
                                                        .is(appCode))),
                        Connection.class,
                        this.getCollectionName())
                .distinct(c -> c.getConnectionDetails() == null ? "" : c.getConnectionDetails().toString());
    }

    /**
     * Refuse a connection pointing anywhere inside our own network before it is
     * stored. Both write paths go through here: create below, and update via
     * {@link #updatableEntity(Connection)}.
     *
     * Mono.defer, not a bare call, so the refusal arrives as an error signal on
     * the returned publisher rather than as a synchronous throw out of the
     * controller method.
     */
    @Override
    public Mono<Connection> create(Connection entity) {
        return Mono.defer(() -> {
            OutboundUrlUtil.validateConnectionDetails(entity.getConnectionDetails());
            return super.create(entity);
        });
    }

    @Override
    public Mono<Connection> read(String id) {
        return super.read(id)
                .flatMap(e -> FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, ca -> {
                    if (ca.getClientCode().equals(e.getClientCode()))
                        return Mono.just(e);

                    Connection cc = new Connection(e);
                    cc.setConnectionDetails(null);
                    return Mono.just(cc);
                }));
    }

    @Override
    protected Mono<Connection> updatableEntity(Connection entity) {
        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        AbstractMongoMessageResourceService.VERSION_MISMATCH);

            OutboundUrlUtil.validateConnectionDetails(entity.getConnectionDetails());

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

    // Only being used for oauth connections
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
                    if (actualConn.getConnectionType() != ConnectionType.NOTIFICATION)
                        return Mono.just(actualConn);

                    final Connection copyConn = new Connection(actualConn);
                    if (copyConn.getConnectionDetails() == null || copyConn.getConnectionDetails().isEmpty())
                        return Mono.just(actualConn);

                    return Flux.fromIterable(copyConn.getConnectionDetails().entrySet())
                            .flatMap(entry -> StringUtil.safeIsBlank(entry.getValue())
                                    ? Mono.just(Tuples.<String, Object>of(entry.getKey(), ""))
                                    : this.readInternal(entry.getValue().toString(), appCode, clientCode)
                                            .map(ObjectWithUniqueID::getObject)
                                            .map(e -> Tuples.<String, Object>of(entry.getKey(), e)))
                            .collectMap(Tuple2::getT1, Tuple2::getT2).map(copyConn::setConnectionDetails);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ConnectionService.readInternalConnection"));
    }

    public Mono<NotificationConnectionDetails> getNotificationConnections(String connectionName, String appCode,
            String urlClientCode, String clientCode) {

        return FlatMapUtil.flatMapMono(
                () -> this.readInternal(connectionName, appCode, urlClientCode, clientCode)
                        .map(ObjectWithUniqueID::getObject)
                        .filter(conn -> conn.getConnectionType() == ConnectionType.NOTIFICATION),

                nConn -> {
                    if (nConn.getConnectionDetails() == null || nConn.getConnectionDetails().isEmpty())
                        return Mono.empty();

                    String mailConnectionName = StringUtil.safeValueOf(nConn.getConnectionDetails().get("mail"));
                    boolean inApp = nConn.getConnectionDetails().containsKey("inapp");

                    if (mailConnectionName == null || mailConnectionName.isEmpty())
                        return Mono.just(new NotificationConnectionDetails(inApp, null));

                    return this.readInternal(mailConnectionName, appCode, urlClientCode, clientCode)
                            .map(ObjectWithUniqueID::getObject)
                            .filter(conn -> conn.getConnectionType() == ConnectionType.MAIL)
                            .map(conn -> new NotificationConnectionDetails(inApp, conn))
                            .defaultIfEmpty(new NotificationConnectionDetails(inApp, null));
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ConnectionService.getNotificationConnections"));
    }

    public Mono<Connection> getConnection(String connectionName, String appCode, String urlClientCode,
            String clientCode, ConnectionType connectionType) {

        return this.readInternal(connectionName, appCode, urlClientCode, clientCode)
                .map(ObjectWithUniqueID::getObject)
                .filter(conn -> conn.getConnectionType() == connectionType);
    }
}
