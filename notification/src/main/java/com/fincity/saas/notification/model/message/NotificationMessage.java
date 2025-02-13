package com.fincity.saas.notification.model.message;

import java.util.Map;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.ChannelType;

import lombok.Data;
import lombok.experimental.Accessors;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Data
@Accessors(chain = true)
public class NotificationMessage implements ChannelType {

	private String messageId;
	private String subject;
	private String body;

	public NotificationMessage() {
		this.messageId = UniqueUtil.shortUUID();
	}

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

	public Map<String, String> toMap() {
		return Map.of(
				"subject", this.getSubject(),
				"body", this.getBody()
		);
	}

	public Tuple2<String, String> toTuple() {
		return Tuples.of(
				this.getSubject() != null ? this.getSubject() : "",
				this.getBody() != null ? this.getBody() : ""
		);
	}

	public boolean isNull() {
		return StringUtil.safeIsBlank(subject) && StringUtil.safeIsBlank(body);
	}
}
