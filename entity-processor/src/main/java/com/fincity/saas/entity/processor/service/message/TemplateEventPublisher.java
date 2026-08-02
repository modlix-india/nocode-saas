package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.configuration.MessagingRabbitConfig;
import com.fincity.saas.entity.processor.oserver.message.model.MessageTemplateQueObject;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TemplateEventPublisher {

    @Value("${entity-processor.whatsapp.mq.exchange:whatsapp.templates}")
    private String exchange;

    private final AmqpTemplate amqpTemplate;

    public TemplateEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    /**
     * Publishes onto the holding queue for the given slot. The routing key deliberately reuses
     * {@link MessagingRabbitConfig#HOLDING_QUEUE_PREFIX} rather than a separate config key, because
     * a mismatch between the two would route to a nonexistent binding and drop the message with no
     * error.
     */
    public Mono<Void> publish(MessageTemplateQueObject queObj, int slotIndex) {

        String routingKey = MessagingRabbitConfig.HOLDING_QUEUE_PREFIX + slotIndex;

        return Mono.just(queObj)
                .flatMap(q -> Mono.deferContextual(ctx -> {
                    if (!ctx.hasKey(LogUtil.DEBUG_KEY)) return Mono.just(q);
                    q.setXDebug(ctx.get(LogUtil.DEBUG_KEY).toString());
                    return Mono.just(q);
                }))
                .flatMap(q -> Mono.fromRunnable(() -> amqpTemplate.convertAndSend(exchange, routingKey, q)));
    }
}
