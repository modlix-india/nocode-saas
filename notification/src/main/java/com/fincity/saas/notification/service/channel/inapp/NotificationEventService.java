package com.fincity.saas.notification.service.channel.inapp;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.notification.document.common.core.Connection;
import com.fincity.saas.notification.model.message.channel.InAppMessage;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.model.response.SendResponse;
import java.math.BigInteger;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.concurrent.Queues;

@Service(value = "notificationEvent")
public class NotificationEventService extends AbstractInAppService implements IInAppService<NotificationEventService> {

    private static final String EVENT_NAME = "sse-notification-event";

    private static final int BUFFER_SIZE = Queues.SMALL_BUFFER_SIZE;

    private final Many<SendResponse<InAppMessage>> sink;

    public NotificationEventService() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false);
    }

    @Override
    public Mono<Boolean> sendMessage(SendRequest inAppMessage, Connection connection) {
        return Mono.justOrEmpty(SendResponse.ofInApp(inAppMessage))
                .map(sink::tryEmitNext)
                .map(Sinks.EmitResult::isSuccess)
                .onErrorResume(e -> Mono.just(Boolean.FALSE));
    }

    public Flux<ServerSentEvent<SendResponse<InAppMessage>>> subscribeToEvent(
            String appCode, String clientCode, BigInteger userId) {

        if (appCode == null || clientCode == null || userId == null) return Flux.empty();

        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ContextAuthentication::isAuthenticated)
                .flatMapMany(isAuthenticated -> {
                    if (Boolean.FALSE.equals(isAuthenticated)) return Flux.empty();

                    return this.sink
                            .asFlux()
                            .filterWhen(response -> this.isMessageForUser(response, appCode, clientCode, userId))
                            .map(this::getServerSentEvent)
                            .onErrorResume(e -> Flux.empty());
                });
    }

    private ServerSentEvent<SendResponse<InAppMessage>> getServerSentEvent(SendResponse<InAppMessage> response) {
        return ServerSentEvent.<SendResponse<InAppMessage>>builder()
                .id(response.getCode())
                .event(eventName(EVENT_NAME, response.getNotificationType(), response.getCode()))
                .data(response)
                .build();
    }

    private Mono<Boolean> isMessageForUser(
            SendResponse<InAppMessage> message, String appCode, String clientCode, BigInteger userId) {
        return Mono.just(message.getClientCode().equals(clientCode)
                && message.getAppCode().equals(appCode)
                && message.getUserId().equals(userId));
    }
}
