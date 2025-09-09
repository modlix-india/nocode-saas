package com.fincity.saas.notification.configuration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfig {

    private MqNameProvider mqNameProvider;

    @Autowired
    private void setMqNameProvider(MqNameProvider mqNameProvider) {
        this.mqNameProvider = mqNameProvider;
    }

    @Bean
    public Declarables queueDeclarables() {
        List<Declarable> declarableList = new ArrayList<>();

        for (String queueName : mqNameProvider.getAllQueues()) {
            Queue queue = new Queue(queueName, true, false, false);
            declarableList.add(queue);
        }

        return new Declarables(declarableList);
    }
}
