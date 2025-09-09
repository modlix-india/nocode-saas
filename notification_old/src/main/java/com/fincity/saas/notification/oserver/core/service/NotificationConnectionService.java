package com.fincity.saas.notification.oserver.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.oserver.core.document.Connection;
import com.fincity.saas.notification.oserver.core.document.Notification;
import com.fincity.saas.notification.oserver.core.enums.ConnectionType;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotificationConnectionService extends AbstractCoreService<Connection> {

    private static final String NOTIFICATION_CONNECTION_TYPE = ConnectionType.NOTIFICATION.name();

    private static final String CHANNEL_CONNECTIONS = "channelConnections";

    @Override
    protected String getObjectName() {
        return Notification.class.getSimpleName();
    }

    @Override
    protected Mono<Connection> fetchCoreDocument(
            String appCode, String urlClientCode, String clientCode, String documentName) {
        return super.coreService.getConnection(
                urlClientCode, documentName, appCode, clientCode, NOTIFICATION_CONNECTION_TYPE);
    }

    @SuppressWarnings("unchecked")
    public Mono<Map<NotificationChannelType, String>> getNotificationConnections(
            String appCode, String clientCode, String notificationConnectionName) {

        if (notificationConnectionName == null || notificationConnectionName.isEmpty()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.securityService.appInheritance(appCode, clientCode, clientCode),
                inheritance ->
                        super.getDocument(appCode, clientCode, clientCode, inheritance, notificationConnectionName),
                (inheritance, connection) -> {
                    if (connection == null || !connection.getConnectionDetails().containsKey(CHANNEL_CONNECTIONS))
                        return Mono.empty();

                    return Mono.just(NotificationChannelType.getChannelTypeMap((Map<String, String>)
                            connection.getConnectionDetails().get(CHANNEL_CONNECTIONS)));
                });
    }
}
