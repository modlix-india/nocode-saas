package com.fincity.saas.core.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.enums.notification.NotificationType;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.core.document.Notification;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.repository.NotificationRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class NotificationService extends AbstractOverridableDataService<Notification, NotificationRepository> {

	private final ConnectionService connectionService;

	protected NotificationService(ConnectionService connectionService) {
		super(Notification.class);
		this.connectionService = connectionService;
	}

	@Override
	protected Mono<Notification> updatableEntity(Notification entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {
					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setNotificationType(entity.getNotificationType());
					existing.setChannelDetails(entity.getChannelDetails());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
	}

	@Override
	public Mono<Notification> create(Notification entity) {

		entity.setName(UniqueUtil.uniqueName(32, entity.getAppCode(), entity.getName()));

		return FlatMapUtil.flatMapMono(

				() -> this.validate(entity),

				super::create).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.create"));
	}

	public Mono<Notification> validate(Notification entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {
					if (entity.getNotificationType() == null
							|| !NotificationType.isLiteralValid(entity.getNotificationType()))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST,
										"Invalid notification type give: $"),
								entity.getNotificationType());

					return Mono.just(entity);
				},

				(ca, vEntity) -> this.validateConnections(entity.getAppCode(), entity.getClientCode(),
						entity.getChannelConnections()),

				(ca, vEntity, validConnections) -> {

					for (Map.Entry<String, Notification.NotificationTemplate> channelEntry : vEntity.getChannelDetails()
							.entrySet()) {
						if (!channelEntry.getValue().isValidSchema())
							return this.messageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.BAD_REQUEST,
											"Invalid notification template schema give: $"),
									entity.getNotificationType());
					}

					return Mono.just(vEntity);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
	}

	private Mono<Boolean> validateConnections(String appCode, String clientCode, Map<String, String> connections) {
		return Flux.fromIterable(connections.values())
				.flatMap(connection -> this.connectionService
						.hasConnection(connection, appCode, clientCode, ConnectionType.NOTIFICATION)
						.flatMap(hasConnection -> {
							if (Boolean.FALSE.equals(hasConnection))
								return this.messageResourceService.throwMessage(
										msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
										CoreMessageResourceService.CONNECTION_NOT_AVAILABLE, connection);
							return Mono.just(Boolean.TRUE);
						}))
				.then(Mono.just(Boolean.TRUE));
	}

	@Override
	public Mono<Notification> update(Notification entity) {
		return FlatMapUtil.flatMapMono(

				() -> this.validate(entity),

				super::update).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.create"));
	}

	public Mono<Notification> getNotification(String name, String appCode, String clientCode,
			NotificationType notificationType) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(name, appCode, clientCode).map(ObjectWithUniqueID::getObject),

				notification -> Mono
						.justOrEmpty(notification.getNotificationType().equals(notificationType.getLiteral())
								? notification
								: null),

				(notification,
						typedNotification) -> Mono.justOrEmpty(typedNotification.getClientCode().equals(clientCode)
								? typedNotification
								: null)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.getNotification"));
	}

	public Mono<Notification> getNotification(String id) {
		return this.read(id);
	}

}
