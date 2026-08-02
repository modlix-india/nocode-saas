package com.fincity.saas.entity.processor.configuration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Staggered delivery for ticket-triggered WhatsApp templates.
 *
 * <p>Publishers send to {@code whatsapp.templates} with routing key {@code whatsapp.hold.N}, where
 * N is the config's order. Each holding queue carries an {@code x-message-ttl} of
 * {@code N * delayStepMs}, so a message waits there before dead-lettering into
 * {@code whatsapp.outbox}, which the listener consumes. A higher N means a longer delay, and that
 * is what spaces the sends out.
 */
@Configuration
public class MessagingRabbitConfig {

    public static final String HOLDING_QUEUE_PREFIX = "whatsapp.hold.";

    @Value("${entity-processor.whatsapp.mq.exchange:whatsapp.templates}")
    private String templateExchangeName;

    @Value("${entity-processor.whatsapp.mq.outbox:whatsapp.outbox}")
    private String outboxQueueName;

    @Value("${entity-processor.whatsapp.mq.dlx:whatsapp.dlx}")
    private String dlxName;

    @Value("${entity-processor.whatsapp.mq.holding.count:5}")
    private int holdingQueueCount;

    @Value("${entity-processor.whatsapp.mq.delay.step.ms:30000}")
    private long delayStepMs;

    /**
     * The exchange publishers target. Without this bean the holding queues are unreachable and
     * every published template is silently dropped.
     */
    @Bean
    public DirectExchange whatsappTemplateExchange() {
        return new DirectExchange(templateExchangeName);
    }

    @Bean
    public DirectExchange whatsappDlx() {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue whatsappOutbox() {
        return QueueBuilder.durable(outboxQueueName).build();
    }

    @Bean
    public Binding whatsappOutboxBinding(Queue whatsappOutbox, DirectExchange whatsappDlx) {
        return BindingBuilder.bind(whatsappOutbox).to(whatsappDlx).with(outboxQueueName);
    }

    /**
     * One holding queue per delay slot, each bound to the template exchange under its own routing
     * key so a publisher can select a slot by name.
     */
    @Bean
    public Declarables whatsappHoldingQueues(DirectExchange whatsappTemplateExchange) {

        List<Declarable> declarables = new ArrayList<>(holdingQueueCount * 2);

        for (int i = 0; i < holdingQueueCount; i++) {
            String queueName = HOLDING_QUEUE_PREFIX + i;

            Queue holdingQueue = QueueBuilder.durable(queueName)
                    .withArgument("x-message-ttl", i * delayStepMs)
                    .withArgument("x-dead-letter-exchange", dlxName)
                    .withArgument("x-dead-letter-routing-key", outboxQueueName)
                    .build();

            declarables.add(holdingQueue);
            declarables.add(BindingBuilder.bind(holdingQueue)
                    .to(whatsappTemplateExchange)
                    .with(queueName));
        }

        return new Declarables(declarables);
    }
}
