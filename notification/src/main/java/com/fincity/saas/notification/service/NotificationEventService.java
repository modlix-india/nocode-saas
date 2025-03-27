package com.fincity.saas.notification.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.notification.model.response.NotificationResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.concurrent.Queues;

@Service
public class NotificationEventService {

    private final Many<NotificationResponse> sink;

    public NotificationEventService() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE);
    }

    public void emit(NotificationResponse notification) {
        sink.tryEmitNext(notification);
    }

    public Flux<ServerSentEvent<NotificationResponse>> subscribe() {
        return SecurityContextUtil.getUsersContextAuthentication()
            .flatMapMany(auth -> sink.asFlux()
                .filter(notification -> notification.getUserId().equals(auth.getUser().getId()))
                .map(notification -> ServerSentEvent.<NotificationResponse>builder()
                    .id(String.valueOf(System.currentTimeMillis()))
                    .event("notification")
                    .data(notification)
                    .build()
                )
            );
    }
}