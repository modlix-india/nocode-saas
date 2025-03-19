package com.fincity.saas.notification.configuration;

import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fincity.saas.notification.enums.NotificationChannelType;

@Component
public class QueueNameProvider {

	@Value("${events.mq.exchange.fanout:notification.fanout.exchange}")
	private String fanoutExchangeName;

	public String[] getEmailBroadcastQueues() {
		return new String[]{
				NotificationChannelType.EMAIL.getQueueName(fanoutExchangeName)
		};
	}

	public String[] getInAppBroadcastQueues() {
		return new String[]{
				NotificationChannelType.IN_APP.getQueueName(fanoutExchangeName)
		};
	}

	public String[] getAllBroadcastQueues() {
		return Stream.of(
				getEmailBroadcastQueues(),
				getInAppBroadcastQueues()
		).flatMap(Stream::of).toArray(String[]::new);
	}
}
