package com.fincity.saas.notification.mq.action.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.channel.ChannelType;
import com.fincity.saas.notification.exception.NotificationDeliveryException;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.service.NotificationConnectionService;
import com.fincity.saas.notification.service.SentNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@Service
public abstract class AbstractMessageService<T extends AbstractMessageService<T>>
        implements IMessageService<T>, ChannelType {

    private NotificationConnectionService connectionService;

    private SentNotificationService sentNotificationService;

    @Autowired
    private void setConnectionService(NotificationConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Autowired
    private void setSentNotificationService(SentNotificationService sentNotificationService) {
        this.sentNotificationService = sentNotificationService;
    }

    private boolean isValid(SendRequest sendRequest) {
        return sendRequest != null && sendRequest.isValid(this.getChannelType());
    }

    private Mono<Connection> getConnection(SendRequest request) {
        return this.connectionService.getNotificationConn(
                request.getAppCode(),
                request.getClientCode(),
                request.getConnections().get(this.getChannelType().getLiteral()));
    }

    private Mono<Boolean> notificationSent(SendRequest sendRequest) {
        return this.sendNotification(sendRequest, NotificationDeliveryStatus.SENT)
                .map(request -> Boolean.TRUE);
    }

    private Mono<Boolean> notificationFailed(SendRequest sendRequest) {
        return this.sendNotification(sendRequest, NotificationDeliveryStatus.FAILED)
                .map(request -> Boolean.FALSE);
    }

    private Mono<SendRequest> sendNotification(SendRequest sendRequest, NotificationDeliveryStatus deliveryStatus) {
        return sentNotificationService.toNetworkNotification(sendRequest, deliveryStatus);
    }

    @Override
    public Mono<Boolean> execute(SendRequest request) {

        if (!isValid(request)) return Mono.just(Boolean.FALSE);

        return FlatMapUtil.flatMapMono(
                () -> this.getConnection(request)
                        .map(connection -> Tuples.of(request, connection))
                        .onErrorResume(
                                GenericException.class,
                                ex -> Mono.just(Tuples.of(
                                        request.setChannelErrorInfo(ex, this.getChannelType()), new Connection()))),
                connection -> {
                    if (connection.getT1().isError(this.getChannelType()))
                        return Mono.just(Tuples.of(connection.getT1(), Boolean.FALSE));

                    return this.execute(request, connection.getT2())
                            .map(executed -> Tuples.of(request, executed))
                            .onErrorResume(
                                    NotificationDeliveryException.class,
                                    ex -> Mono.just(Tuples.of(
                                            request.setChannelErrorInfo(ex, this.getChannelType()), Boolean.FALSE)));
                },
                (connection, executed) -> executed.getT1().isError(this.getChannelType())
                        ? this.notificationFailed(executed.getT1())
                        : this.notificationSent(executed.getT1()));
    }
}
