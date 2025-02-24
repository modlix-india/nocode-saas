package com.fincity.saas.core.service;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.enums.notification.NotificationType;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.core.document.Notification;
import com.fincity.saas.core.repository.NotificationRepository;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class NotificationService extends AbstractOverridableDataService<Notification, NotificationRepository> {

	public static final String CACHE_NAME_NOTIFICATION = "notification";

	private final CoreSchemaService coreSchemaService;

	private final Gson gson;

	protected NotificationService(CoreSchemaService coreSchemaService, Gson gson) {
		super(Notification.class);

		this.gson = gson;
		this.coreSchemaService = coreSchemaService;
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
				}
		).contextWrite(Context.of(LogUtil.METHOD_NAME, "NotificationService.updatableEntity"));
	}

	@Override
	public Mono<Notification> create(Notification entity) {

		entity.setName(UniqueUtil.uniqueName(32, entity.getAppCode(), entity.getName()));

		return super.create(entity);
	}

	private Mono<Notification> localCreate(Notification entity) {

		return null;
	}

	public Mono<Notification> validate(Notification entity) {

		return FlatMapUtil.flatMapMono(

				() -> {
					if (entity.getNotificationType() == null || !NotificationType.isLiteralValid(entity.getNotificationType()))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST,
										"Invalid notification type give: $"), entity.getNotificationType());

					return Mono.just(entity);
				},

				vEntity -> {

					for (Map.Entry<String, Notification.NotificationTemplate> channelEntry : vEntity.getChannelDetails().entrySet()) {
						if (!channelEntry.getValue().isValidSchema())
							return this.messageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.BAD_REQUEST,
											"Invalid notification template schema give: $"), entity.getNotificationType());
					}

					return Mono.just(vEntity);
				});
	}

	@Override
	public Mono<Notification> update(Notification entity) {
		return super.update(entity);
	}

	@Override
	public Mono<Boolean> delete(String id) {
		return super.delete(id);
	}
}
