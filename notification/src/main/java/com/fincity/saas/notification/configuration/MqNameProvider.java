package com.fincity.saas.notification.configuration;

import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;

@Component
public class MqNameProvider {

	@Value("${events.mq.exchange.fanout:notification.fanout.exchange}")
	private String fanoutExchangeName;

	public String[] getEmailBroadcastQueues() {
		return new String[]{
				NotificationChannelType.EMAIL.getMqQueueName(fanoutExchangeName)
		};
	}

	public String[] getInAppBroadcastQueues() {
		return new String[]{
				NotificationChannelType.IN_APP.getMqQueueName(fanoutExchangeName)
		};
	}

	public String[] getAllBroadcastQueues() {
		return Stream.of(
				getEmailBroadcastQueues(),
				getInAppBroadcastQueues()
		).flatMap(Stream::of).toArray(String[]::new);
	}
}
