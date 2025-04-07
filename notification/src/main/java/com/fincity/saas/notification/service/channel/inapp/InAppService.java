package com.fincity.saas.notification.service.channel.inapp;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.mq.action.service.AbstractMessageService;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class InAppService extends AbstractMessageService<InAppService> {

	private final NotificationEventService eventService;

	private final Map<String, IInAppService<?>> services = new HashMap<>();
	private final NotificationMessageResourceService msgService;

	public InAppService(NotificationEventService eventService, NotificationMessageResourceService msgService) {
		this.eventService = eventService;
		this.msgService = msgService;
	}

	@PostConstruct
	public void init() {
		this.services.put("sse", eventService);
	}

	@Override
	public Mono<Boolean> execute(SendRequest message, Connection connection) {

		return FlatMapUtil.flatMapMono(

				() -> Mono.justOrEmpty(this.services.get("sse"))
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								NotificationMessageResourceService.CONNECTION_DETAILS_MISSING,
								connection.getConnectionSubType())),

				service -> service.sendMessage(message, connection)

		).switchIfEmpty(Mono.just(Boolean.FALSE))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "InAppService.execute"));
	}

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
	}
}
