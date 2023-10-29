package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.repository.PageRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PageService extends AbstractOverridableDataService<Page, PageRepository> {

	private ApplicationService appServiceForProps;

	public PageService() {
		super(Page.class);
	}

	public void setApplicationService(ApplicationService appService) {
		this.appServiceForProps = appService;
	}

	@Override
	protected Mono<Page> updatableEntity(Page entity) {

		return flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {
					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setDevice(entity.getDevice())
							.setTranslations(entity.getTranslations())
							.setProperties(entity.getProperties())
							.setEventFunctions(entity.getEventFunctions())
							.setRootComponent(entity.getRootComponent())
							.setComponentDefinition(entity.getComponentDefinition())
							.setPermission(entity.getPermission());

					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.updatableEntity"));
	}

	@Override
	public Mono<ObjectWithUniqueID<Page>> read(String name, String appCode, String clientCode) {

		return super.read(name, appCode, clientCode).flatMap(pg -> {

			if (StringUtil.safeIsBlank(pg.getObject().getPermission()))
				return Mono.just(pg);

			return flatMapMono(

					SecurityContextUtil::getUsersContextAuthentication,

					ca -> Mono.just(ca.isAuthenticated()),

					(ca, isAuthenticated) -> {

						if (isAuthenticated.booleanValue())
							return Mono.just(pg);

						return flatMapMono(() -> appServiceForProps.readProperties(appCode, appCode, clientCode),

								props -> {

									if (StringUtil.safeIsBlank(props.get("loginPage")))
										return Mono.just(pg);

									return super.read(props.get("loginPage")
											.toString(), appCode, clientCode);
								}).contextWrite(
										Context.of(LogUtil.METHOD_NAME, "PageService.read [Looking for Login page]"));
					}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.read"));
		})
				.switchIfEmpty(Mono
						.defer(() -> flatMapMono(() -> appServiceForProps.readProperties(appCode, appCode, clientCode),

								props -> {

									if (StringUtil.safeIsBlank(props.get("notFoundPage")))
										return Mono.empty();

									return super.read(props.get("notFoundPage")
											.toString(), appCode, clientCode);
								}).contextWrite(Context.of(LogUtil.METHOD_NAME,
										"PageService.read [Looking for Not found page]"))));
	}

	@Override
	protected Mono<ObjectWithUniqueID<Page>> applyChange(String name, String appCode, String clientCode, Page page,
			String checksumString) {

		return flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.appServiceForProps.readProperties(appCode, appCode, clientCode),

				(ca, props) -> {

					if (!SecurityContextUtil.hasAuthority(page.getPermission(), ca.getAuthorities())) {

						if (StringUtil.safeIsBlank(props.get("forbiddenPage")))
							return this.messageResourceService.throwMessage(
									msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
									AbstractMongoMessageResourceService.FORBIDDEN_PERMISSION, page.getPermission());

						return super.read(props.get("forbiddenPage")
								.toString(), appCode, clientCode);
					}

					return Mono.just(new ObjectWithUniqueID<>(page, checksumString));
				},

				(ca, app, object) -> {
					object.getObject().setComponentDefinition(object.getObject().getComponentDefinition()
							.entrySet()
							.stream()
							.filter(c -> c.getValue()
									.getPermission() == null || SecurityContextUtil.hasAuthority(
											c.getValue()
													.getPermission(),
											ca.getAuthorities()))
							.collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

					return Mono.just(object);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "PageService.applyChange"))
				.defaultIfEmpty(new ObjectWithUniqueID<>(page, checksumString));

	}
}
