package com.fincity.saas.notification.document;

import java.io.Serial;
import java.util.EnumMap;
import java.util.Map;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.NotificationTemplate;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public class Notification extends AbstractOverridableDTO<Notification> {

	@Serial
	private static final long serialVersionUID = 4924671644117461908L;

	private String notificationType;
	private Map<String, String> channelConnections;
	private Map<String, NotificationTemplate> channelDetails;

	public Notification(Notification notification) {
		super(notification);
		this.notificationType = notification.notificationType;
		this.channelDetails = notification.channelDetails;
	}

	@Override
	public Mono<Notification> applyOverride(Notification base) {
		return Mono.just(this);
	}

	@Override
	public Mono<Notification> makeOverride(Notification base) {
		return Mono.just(this);
	}

	public Map<NotificationChannelType, NotificationTemplate> getChannelDetailMap() {
		Map<NotificationChannelType, NotificationTemplate> resultMap = new EnumMap<>(NotificationChannelType.class);
		this.channelDetails.forEach((key, template) ->
				resultMap.put(NotificationChannelType.lookupLiteral(key), template));
		return resultMap;
	}

	public Map<NotificationChannelType, String> getChannelConnectionMap() {
		Map<NotificationChannelType, String> resultMap = new EnumMap<>(NotificationChannelType.class);
		this.channelConnections.forEach((key, connectionName) ->
				resultMap.put(NotificationChannelType.lookupLiteral(key), connectionName));
		return resultMap;
	}
}
