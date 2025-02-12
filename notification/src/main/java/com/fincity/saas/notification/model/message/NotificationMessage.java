package com.fincity.saas.notification.model.message;

import java.util.Map;

import com.fincity.saas.notification.enums.ChannelType;

import lombok.Data;
import lombok.experimental.Accessors;
import reactor.util.function.Tuple2;

@Data
@Accessors(chain = true)
public class NotificationMessage implements ChannelType {

	private String subject;
	private String body;

	public static NotificationMessage of() {
		return new NotificationMessage();
	}

	public static NotificationMessage of(String subject, String body) {
		return new NotificationMessage().setSubject(subject).setBody(body);
	}

	public static NotificationMessage of(Map<String, String> message) {

		if (message == null || message.isEmpty())
			return NotificationMessage.of();

		String subject = message.getOrDefault("subject", null);
		String body = message.getOrDefault("body", null);
		return NotificationMessage.of(subject, body);
	}

	public static NotificationMessage of(Tuple2<String, String> message) {

		if (message == null)
			return NotificationMessage.of();

		return NotificationMessage.of(message.getT1(), message.getT2());
	}

	public static Map<String, String> toMap(NotificationMessage message) {
		return Map.of(
				"subject", message.getSubject(),
				"body", message.getBody()
		);
	}

	public boolean isNull() {
		return subject == null && body == null;
	}
}
