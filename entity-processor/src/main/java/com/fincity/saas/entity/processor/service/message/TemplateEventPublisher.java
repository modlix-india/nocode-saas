package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.oserver.message.model.MessageTemplateQueObject;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TemplateEventPublisher {

    @Value("${entity.processor.whatsapp.mq.exchange:whatsapp.templates}")
    private String exchange;

    @Value("${entity.processor.whatsapp.mq.queuePrefix:whatsapp.hold.}")
    private String queuePrefix;

    private final AmqpTemplate amqpTemplate;

    public TemplateEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    @PostConstruct
    protected void init() {
        // no-op for now; kept for symmetry with other MQ services
    }

    public Mono<Void> publish(MessageTemplateQueObject queObj, int slotIndex) {

        String routingKey = queuePrefix + slotIndex;

        return Mono.just(queObj)
                .flatMap(q -> Mono.deferContextual(ctx -> {
                    if (!ctx.hasKey(LogUtil.DEBUG_KEY)) return Mono.just(q);
                    q.setXDebug(ctx.get(LogUtil.DEBUG_KEY).toString());
                    return Mono.just(q);
                }))
                .flatMap(q -> Mono.fromCallable(() -> {
                    amqpTemplate.convertAndSend(exchange, routingKey, q);
                    return (Void) null;
                }));
    }
}
