package com.fincity.saas.notification.model.message;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.channel.ChannelType;

import lombok.Data;
import lombok.experimental.Accessors;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Data
@Accessors(chain = true)
public class NotificationMessage<T extends NotificationMessage<T>> implements ChannelType, IRecipientInfo<T>, Serializable {

	@Serial
	private static final long serialVersionUID = 3505135188384990396L;

	private String messageId;
	private String subject;
	private String body;

	public NotificationMessage() {
		this.messageId = UniqueUtil.shortUUID();
	}

	public T setMessage(Map<String, String> message) {
		this.subject = message.getOrDefault("subject", null);
		this.messageId = UniqueUtil.shortUUID();
		this.body = message.getOrDefault("body", null);
		return (T) this;
	}

	public T updateMessage(String subject, String body) {
		this.subject = subject;
		this.body = body;
		return (T) this;
	}

	public Tuple2<String, String> toTuple() {
		return Tuples.of(
				this.getSubject() != null ? this.getSubject() : "",
				this.getBody() != null ? this.getBody() : "");
	}

	public boolean isNull() {
		return StringUtil.safeIsBlank(subject) && StringUtil.safeIsBlank(body);
	}
}
