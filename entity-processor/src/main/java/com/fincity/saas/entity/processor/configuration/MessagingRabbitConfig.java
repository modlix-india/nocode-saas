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

@Configuration
public class MessagingRabbitConfig {

    @Value("${entity.processor.whatsapp.mq.outbox:whatsapp.outbox}")
    private String outboxQueueName;

    @Value("${entity.processor.whatsapp.mq.dlx:whatsapp.dlx}")
    private String dlxName;

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
        return QueueBuilder.durable(outboxQueueName).build();
    }

    @Bean
    public Binding whatsappOutboxBinding(Queue whatsappOutbox, DirectExchange whatsappDlx) {
        return BindingBuilder.bind(whatsappOutbox).to(whatsappDlx).with(outboxQueueName);
    }

    @Bean
    public Declarables whatsappHoldingQueues() {
        List<Declarable> declarable = new ArrayList<>(holdingQueueCount);

        for (int i = 0; i < holdingQueueCount; i++) {
            long ttl = i * delayStepMs;
            String queueName = "whatsapp.hold." + i;

            declarable.add(QueueBuilder.durable(queueName)
                    .withArgument("x-message-ttl", ttl)
                    .withArgument("x-dead-letter-exchange", dlxName)
                    .withArgument("x-dead-letter-routing-key", outboxQueueName)
                    .build());
        }

        return new Declarables(declarable);
    }
}
