package com.fincity.saas.notification.mq;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.configuration.MqNameProvider;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.service.SentNotificationService;
import java.util.Arrays;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotificationMessageProducer {

    private AmqpTemplate amqpTemplate;
    private MqNameProvider mqNameProvider;
    private SentNotificationService sentNotificationService;

    @Autowired
    public void setAmqpTemplate(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    @Autowired
    private void setSentNotificationService(SentNotificationService sentNotificationService) {
        this.sentNotificationService = sentNotificationService;
    }

    @Autowired
    private void setMqNameProvider(MqNameProvider mqNameProvider) {
        this.mqNameProvider = mqNameProvider;
    }

    public Mono<SendRequest> sendToQueues(SendRequest sendRequest) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.deferContextual(ctx -> {
                    boolean isDebug = ctx.hasKey(LogUtil.DEBUG_KEY);

                    return isDebug
                            ? Mono.just(sendRequest.setXDebug(ctx.get(LogUtil.DEBUG_KEY)))
                            : Mono.just(sendRequest);
                }),
                dRequest -> sentNotificationService.toGatewayNotification(dRequest, NotificationDeliveryStatus.QUEUED),
                (dRequest, sRequest) -> Mono.fromCallable(() -> {
                    // Send to each queue directly
                    Arrays.stream(mqNameProvider.getAllQueues())
                            .forEach(queueName -> amqpTemplate.convertAndSend("", queueName, dRequest));
                    return dRequest;
                }));
    }
}
