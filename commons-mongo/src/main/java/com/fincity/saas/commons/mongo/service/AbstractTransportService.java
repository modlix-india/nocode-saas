package com.fincity.saas.commons.mongo.service;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;
import com.fincity.saas.commons.mongo.model.TransportObject;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractTransportService extends AbstractOverridableDataService<Transport, TransportRepository> {

	protected AbstractTransportService() {
		super(Transport.class);
	}

	@Override
	public Mono<Transport> create(Transport entity) {

		entity.setName(StringUtil.safeValueOf(entity.getName(), "") + entity.getUniqueTransportCode());

		return this
				.readAllFilter(new ComplexCondition()
						.setConditions(List.of(new FilterCondition().setField("uniqueTransportCode")
								.setValue(entity.getUniqueTransportCode()))))
				.collectList()
				.flatMap(e -> e.isEmpty() ? super.create(entity)
						: this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.CONFLICT, msg),
								AbstractMongoMessageResourceService.UNABLE_TO_CREATE_OBJECT,
								"because transport with " + entity.getUniqueTransportCode() + " already exits"));

	}

	public Mono<Boolean> applyTransportWithTransportCode(String transportCode) {

		return this.readAllFilter(new ComplexCondition()
				.setConditions(List.of(new FilterCondition().setField("uniqueTransportCode")
						.setValue(transportCode))))
				.collectList()
				.flatMap(e -> e.isEmpty() ? this.messageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
						AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Transport", transportCode)
						: this.applyTransport(e.get(0).getId()));

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Mono<Boolean> applyTransport(String id) {

		return FlatMapUtil.flatMapMono(

				() -> this.read(id),

				transport -> SecurityContextUtil.getUsersContextAuthentication(),

				(transport, ca) -> {

					var serviceMap = this.getServieMap()
							.stream()
							.collect(Collectors.toMap(AbstractOverridableDataService::getObjectName,
									Function.identity()));

					return Flux.fromIterable(transport.getObjects())
							.flatMap(obj -> FlatMapUtil.flatMapMonoWithNull(

									() -> Mono.just(serviceMap.get(obj.getObjectType())),

									service -> Mono.justOrEmpty(service.makeEntity(obj)),

									(service, tentity) -> service
											.read(
													tentity.getName(), tentity.getAppCode(),
													tentity.getClientCode())
											.map(e -> ((ObjectWithUniqueID<? extends AbstractOverridableDTO>) e)
													.getObject()),

									(service, tentity, entity) -> {

										tentity.setId(null);
										tentity.setClientCode(ca.getClientCode());
										tentity.setMessage("From transport : " + transport.getName());

										AbstractOverridableDTO sentity = (AbstractOverridableDTO) entity;

										if (sentity != null
												&& StringUtil.safeEquals(ca.getClientCode(), sentity.getClientCode())) {
											tentity.setVersion(sentity.getVersion());
											tentity.setId(sentity.getId());
											return service.update(tentity);
										}

										tentity.setVersion(1);
										return service.create(tentity);
									},

									(service, tentity, sentity, savedEntity) -> Mono.just(true))
									.contextWrite(
											Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.applyTransport")))
							.collectList()
							.map(e -> true);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.applyTransport"))
				.defaultIfEmpty(false);
	}

	@Override
	public Mono<Transport> update(Transport entity) {

		return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_MODIFIED, msg),
				AbstractMessageService.CANNOT_BE_UPDATED);
	}

	@Override
	protected Mono<Transport> updatableEntity(Transport entity) {
		return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.NOT_MODIFIED, msg),
				AbstractMessageService.CANNOT_BE_UPDATED);
	}

	@Override
	protected Mono<String> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextAuthentication()
				.map(ContextAuthentication::getUser)
				.map(ContextUser::getId)
				.map(Object::toString);
	}

	@SuppressWarnings("unchecked")
	public Mono<Transport> makeTransport(TransportRequest request) {

		Transport to = new Transport();

		to.setAppCode(request.getAppCode());
		to.setClientCode(request.getClientCode());
		to.setName(request.getName());
		to.setUniqueTransportCode(UniqueUtil.shortUUID());
		to.setType(this.getTransportType());

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.accessCheck(ca, CREATE, to, false),

				(ca, hasPermission) -> {

					if (!hasPermission.booleanValue()) {
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								AbstractMongoMessageResourceService.FORBIDDEN_CREATE, "Transport");
					}

					return Flux.fromIterable(this.getServieMap())
							.flatMap(e -> {
								List<String> list = request.getObjectList() == null ? null
										: request.getObjectList()
												.get(e.getObjectName());
								if (request.getObjectList() != null && !request.getObjectList()
										.isEmpty() && list == null)
									return Flux.empty();

								Flux<TransportObject> x = e // NOSONAR
										.readForTransport(request.getAppCode(), request.getClientCode(), list)
										.map(e::makeTransportObject);

								// For some reason it is not identifying the type when returning immediately

								return x;
							})
							.collectList()
							.map(to::setObjects);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportService.makeTransport"));
	}

	@SuppressWarnings("rawtypes")
	public abstract List<AbstractOverridableDataService> getServieMap();

	protected abstract String getTransportType();
}
