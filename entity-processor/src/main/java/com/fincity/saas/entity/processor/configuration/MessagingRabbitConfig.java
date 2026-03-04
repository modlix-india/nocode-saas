package com.fincity.saas.entity.processor.configuration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingRabbitConfig {

    @Value("${entity.processor.whatsapp.mq.outbox:whatsapp.outbox}")
    private String outboxQueueName;

    @Value("${entity.processor.whatsapp.mq.dlx:whatsapp.dlx}")
    private String dlxName;

    @Value("${entity.processor.whatsapp.mq.failure.dlx:whatsapp.failure.dlx}")
    private String failureDlxName;

    @Value("${entity.processor.whatsapp.mq.failure.dlq:whatsapp.failure.dlq}")
    private String failureDlqName;

    @Value("${entity.processor.whatsapp.mq.holding.count:5}")
    private int holdingQueueCount;

    @Value("${entity.processor.whatsapp.mq.delay.step.ms:30000}")
    private long delayStepMs;

    @Bean
    public DirectExchange whatsappDlx() {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue whatsappOutbox() {
        return QueueBuilder.durable(outboxQueueName)
                .withArgument("x-dead-letter-exchange", failureDlxName)
                .withArgument("x-dead-letter-routing-key", outboxQueueName)
                .build();
    }

    @Bean
    public Binding whatsappOutboxBinding(Queue whatsappOutbox, DirectExchange whatsappDlx) {
        return BindingBuilder.bind(whatsappOutbox).to(whatsappDlx).with(outboxQueueName);
    }

    @Bean
    public List<Queue> whatsappHoldingQueues() {
        List<Queue> queues = new ArrayList<>(holdingQueueCount);

        for (int i = 0; i < holdingQueueCount; i++) {
            long ttl = i * delayStepMs;
            String queueName = "whatsapp.hold." + i;

            queues.add(QueueBuilder.durable(queueName)
                    .withArgument("x-message-ttl", ttl)
                    .withArgument("x-dead-letter-exchange", dlxName)
                    .withArgument("x-dead-letter-routing-key", outboxQueueName)
                    .build());
        }

        return queues;
    }

    @Bean
    public DirectExchange whatsappFailureDlx() {
        return new DirectExchange(failureDlxName);
    }

    @Bean
    public Queue whatsappFailureDlq() {
        return QueueBuilder.durable(failureDlqName).build();
    }

    @Bean
    public Binding whatsappFailureDlqBinding(Queue whatsappFailureDlq, DirectExchange whatsappFailureDlx) {
        return BindingBuilder.bind(whatsappFailureDlq).to(whatsappFailureDlx).with(outboxQueueName);
    }
}
