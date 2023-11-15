package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;

import java.security.Security;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.ui.document.Personalization;
import com.fincity.saas.ui.repository.PersonalizationRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PersonalizationService extends AbstractOverridableDataService<Personalization, PersonalizationRepository> {

	protected PersonalizationService() {
		super(Personalization.class);
	}

	@Override
	public Mono<Personalization> create(Personalization entity) {

		entity.setBaseClientCode(null);
		return super.create(entity);
	}

	@Override
	protected Mono<Personalization> updatableEntity(Personalization entity) {

		return flatMapMono(

				() -> this.read(entity.getId()),

				existing -> SecurityContextUtil.getUsersContextAuthentication(),

				(existing, ca) -> {

					if (!existing.getCreatedBy()
							.equals(ca.getUser()
									.getId()
									.toString()))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								AbstractMongoMessageResourceService.CANNOT_CHANGE_PREF);

					if (existing.getVersion() != entity.getVersion())

						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setPersonalization(entity.getPersonalization());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PersonalizationService.updatableEntity"));
	}

	@Override
	protected boolean isVersionable() {
		return false;
	}

	@Override
	protected Mono<Boolean> accessCheck(ContextAuthentication ca, String method, String appode, String clientCode,
			boolean checkAppWriteAccess) {

		return Mono
				.just(CREATE.equals(method) || UPDATE.equals(method) || DELETE.equals(method) || READ.equals(method));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return this.read(id)
				.flatMap(e -> SecurityContextUtil.getUsersContextUser().flatMap(u -> {

					if (!e.getCreatedBy().equals(u.getId().toString()))
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								AbstractMongoMessageResourceService.CANNOT_CHANGE_PREF);

					return Mono.just(e);
				})).flatMap(x -> super.delete(id));
	}

	public Mono<Map<String, Object>> addPersonalization(String appName, String name,
			Map<String, Object> personalization) {

		return flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca == null ? Mono.empty()
						: this.repo.findOneByNameAndAppCodeAndClientCode(name + ca.getUser().getId(), appName,
								ca.getClientCode()),

				(ca, person) -> {

					if (ca == null)
						return Mono.just(personalization);

					if (person == null) {
						person = (Personalization) (new Personalization().setName(name + ca.getUser().getId())
								.setAppCode(appName)
								.setClientCode(ca.getClientCode()));

						person.setPersonalization(personalization);
						return this.create(person)
								.map(Personalization::getPersonalization);
					}

					person.setPersonalization(personalization);
					return this.update(person)
							.map(Personalization::getPersonalization);
				}

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "PersonalizationService.addPersonalization"))
				.defaultIfEmpty(Map.of());
	}

	public Mono<Map<String, Object>> getPersonalization(String appName, String name) {

		return flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca == null ? Mono.empty()
						: this.repo
								.findOneByNameAndAppCodeAndClientCode(name + ca.getUser().getId(), appName,
										ca.getClientCode())
								.map(Personalization::getPersonalization)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "PersonalizationService.getPersonalization"))
				.defaultIfEmpty(Map.of());

	}

	public Mono<Boolean> deletePersonalization(String appName, String name) {
		return flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> ca == null ? Mono.empty()
						: this.repo
								.findOneByNameAndAppCodeAndClientCode(name + ca.getUser().getId(), appName,
										ca.getClientCode()),

				(ca, person) -> this.delete(person.getId())

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "PersonalizationService.deletePersonalization"))
				.defaultIfEmpty(Boolean.FALSE);
	}
}
