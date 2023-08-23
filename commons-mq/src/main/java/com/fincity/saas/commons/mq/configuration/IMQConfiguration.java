package com.fincity.saas.commons.mq.configuration;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface IMQConfiguration {

	@Bean
	default DirectRabbitListenerContainerFactory directMesageListener(CachingConnectionFactory connectionFactory) {

		DirectRabbitListenerContainerFactory factory = new DirectRabbitListenerContainerFactory();
		factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
		factory.setConsumersPerQueue(1);
		factory.setMessagesPerAck(1);
		factory.setPrefetchCount(0);
		factory.setConnectionFactory(connectionFactory);
		factory.setMessageConverter(new Jackson2JsonMessageConverter());
		return factory;
	}

	@Bean
	default Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper mapper) {
		return new Jackson2JsonMessageConverter(mapper);
	}
}
