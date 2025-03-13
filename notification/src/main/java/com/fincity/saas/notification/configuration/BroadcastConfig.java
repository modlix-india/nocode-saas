package com.fincity.saas.notification.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fincity.saas.notification.enums.NotificationChannelType;

@Configuration
public class BroadcastConfig {

	@Value("${events.mq.exchange.fanout:notification.fanout.exchange}")
	private String fanoutExchangeName;

	@Bean
	public Declarables fanoutBindings() {

		FanoutExchange fanoutExchange = new FanoutExchange(fanoutExchangeName);

		List<Declarable> declarableList = new ArrayList<>();

		declarableList.add(fanoutExchange);

		for (NotificationChannelType channelType : NotificationChannelType.values()) {
			declarableList.add(new Queue(channelType.getQueueName(fanoutExchangeName), true, false, false));
			declarableList.add(BindingBuilder.bind(new Queue(channelType.getQueueName(fanoutExchangeName), false))
					.to(fanoutExchange));
		}

		return new Declarables(declarableList);
	}
}
