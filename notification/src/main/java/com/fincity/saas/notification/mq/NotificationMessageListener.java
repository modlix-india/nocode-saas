package com.fincity.saas.notification.mq;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.configuration.MqNameProvider;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.mq.action.service.IMessageService;
import com.fincity.saas.notification.service.channel.email.EmailService;
import com.rabbitmq.client.Channel;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class NotificationMessageListener {

	private final Map<NotificationChannelType, IMessageService> messageServices = new EnumMap<>(
			NotificationChannelType.class);

	private final EmailService emailService;

	private MqNameProvider mqNameProvider;

	public NotificationMessageListener(EmailService emailService) {
		this.emailService = emailService;
	}

	@Autowired
	private void setMqNameProvider(MqNameProvider mqNameProvider) {
		this.mqNameProvider = mqNameProvider;
	}

	@PostConstruct
	public void init() {
		this.messageServices.put(NotificationChannelType.EMAIL, emailService);
	}

	@RabbitListener(queues = "#{mqNameProvider.getEmailBroadcastQueues()}", containerFactory = "directMessageListener", messageConverter = "jsonMessageConverter")
	public Mono<Void> handleEmailNotification(@Payload SendRequest request, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long tag) {
		return this.executeMessage(NotificationChannelType.EMAIL, request)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationMessageListener.handleEmailNotification"))
				.then();
	}

	@RabbitListener(queues = "#{mqNameProvider.getInAppBroadcastQueues()}", containerFactory = "directMessageListener", messageConverter = "jsonMessageConverter")
	public Mono<Void> handleInAppNotification(@Payload SendRequest request, Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long tag) {
		return this.executeMessage(NotificationChannelType.IN_APP, request)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationMessageListener.handleInAppNotification"))
				.then();
	}

	private Mono<Boolean> executeMessage(NotificationChannelType channelType, SendRequest request) {

		Mono<Boolean> reveicedMono = FlatMapUtil.flatMapMono(

				() -> Mono.justOrEmpty(this.messageServices.get(channelType)),

				service -> service.execute(request).switchIfEmpty(Mono.just(Boolean.FALSE))

		).onErrorResume(throwable -> Mono.just(Boolean.FALSE));

		return request.getXDebug() != null
				? reveicedMono.contextWrite(Context.of(LogUtil.DEBUG_KEY, request.getXDebug()))
				: reveicedMono;
	}

}
