package com.fincity.saas.notification.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BroadcastConfig {

	@Value("${events.mq.exchange.fanout:notification.fanout.exchange}")
	private String fanoutExchangeName;

	private MqNameProvider mqNameProvider;

	@Autowired
	private void setMqNameProvider(MqNameProvider mqNameProvider) {
		this.mqNameProvider = mqNameProvider;
	}

	@Bean
	public Declarables fanoutBindings() {

		FanoutExchange fanoutExchange = new FanoutExchange(fanoutExchangeName);

		List<Declarable> declarableList = new ArrayList<>();

		declarableList.add(fanoutExchange);

		for (String queueName : mqNameProvider.getAllBroadcastQueues()) {
			Queue queue = new Queue(queueName, true, false, false);
			declarableList.add(queue);
			declarableList.add(BindingBuilder.bind(queue).to(fanoutExchange));
		}

		return new Declarables(declarableList);
	}
}
