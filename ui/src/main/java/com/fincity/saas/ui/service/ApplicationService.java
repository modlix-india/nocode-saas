package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ApplicationService extends AbstractOverridableDataService<Application, ApplicationRepository> {

	private static final String CACHE_NAME_PROPERTIES = "cacheProperties";

	private PageService pageService;

	private UIFillerService fillerService;

	@Autowired
	public ApplicationService(PageService pageService, UIFillerService fillerService) {
		super(Application.class);
		this.pageService = pageService;
		this.fillerService = fillerService;
	}

	protected ApplicationService() {
		super(Application.class);
	}

	@PostConstruct
	public void init() {
		// this cyclic reference is need for picking shell page definition & the other
		// page definitions in the page service from application properties.
		this.pageService.setApplicationService(this);
	}

	@Override
	public Mono<Application> create(Application entity) {

		if (StringUtil.safeIsBlank(entity.getName()) || StringUtil.safeIsBlank(entity.getAppCode())
				|| !StringUtil.safeEquals(entity.getName(), entity.getAppCode())
				|| !StringUtil.onlyAlphabetAllowed(entity.getAppCode()))

			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					UIMessageResourceService.APP_NAME_MISMATCH);

		return super.create(entity);
	}

	@Override
	public Mono<Application> update(Application entity) {

		return FlatMapUtil.flatMapMono(
				() -> super.update(entity),
				this::evictAll);
	}

	private Mono<Application> evictAll(Application e) {

		return FlatMapUtil.flatMapMono(
				() -> cacheService.evictAll(
						this.getCacheName(e.getAppCode() + "_" + IndexHTMLService.CACHE_NAME_INDEX, e.getAppCode())),
				x -> cacheService.evictAll(
						this.getCacheName(e.getAppCode() + "_" + ManifestService.CACHE_NAME_MANIFEST, e.getAppCode())),
				(x, y) -> cacheService
						.evictAll(this.getCacheName(e.getAppCode() + "_" + CACHE_NAME_PROPERTIES, e.getAppCode())),
				(x, y, z) -> Mono.just(e));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return this.read(id)
				.flatMap(e -> super.delete(id).flatMap(x -> this.evictAll(e).thenReturn(x)));
	}

	@Override
	protected Mono<Application> updatableEntity(Application entity) {

		return flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {
					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					existing.setProperties(entity.getProperties())
							.setTranslations(entity.getTranslations())
							.setLanguages(entity.getLanguages());

					existing.setMessage(entity.getMessage());
					existing.setDefaultLanguage(entity.getDefaultLanguage());
					existing.setVersion(existing.getVersion() + 1);

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.updatableEntity"));
	}

	public Mono<Map<String, Object>> readProperties(String name, String appCode, String clientCode) { // NOSONAR
		// This method is not complex, it is just long.

		return FlatMapUtil.flatMapMonoWithNull(

				() -> Mono.just(clientCode),

				key -> cacheService.get(this.getCacheName(appCode + "_" + CACHE_NAME_PROPERTIES, appCode), key)
						.map(this.pojoClass::cast),

				(key, cApp) -> {
					if (cApp != null)
						return Mono.just(cApp);

					return SecurityContextUtil.getUsersContextAuthentication()
							.flatMap(ca -> this.readIfExistsInBase(name, appCode, ca.getUrlClientCode(),
									clientCode));
				},

				(key, cApp, dbApp) -> dbApp == null ? Mono.empty() : this.readInternal(dbApp.getId()),

				(key, cApp, dbApp, mergedApp) -> {

					if (cApp == null && mergedApp == null)
						return Mono.empty();

					try {
						return Mono.just(this.pojoClass.getConstructor(this.pojoClass)
								.newInstance(cApp != null ? cApp : mergedApp));
					} catch (Exception e) {

						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
								AbstractMongoMessageResourceService.UNABLE_TO_CREATE_OBJECT, this.getObjectName());
					}
				},

				(key, cApp, dbApp, mergedApp, clonedApp) -> {

					if (clonedApp == null)
						return Mono.empty();

					if (cApp == null && mergedApp != null) {
						cacheService.put(this.getCacheName(appCode + "_" + CACHE_NAME_PROPERTIES, appCode), mergedApp,
								key);
					}

					return Mono.justOrEmpty(clonedApp.getProperties());
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.readProperties"))
				.defaultIfEmpty(Map.of());
	}

	@Override
	protected Mono<ObjectWithUniqueID<Application>> applyChange(String name, String appCode, String clientCode,
			Application object, String id) {

		if (object == null)
			return Mono.empty();

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(ca == null || object.getPermission() == null
						|| SecurityContextUtil.hasAuthority(object.getPermission(), ca.getAuthorities())),

				(ca, showShellPage) -> {

					Map<String, Object> props = object.getProperties();

					if (props == null || props.get("shellPage") == null)
						return Mono.empty();

					Object pageName = props.get("shellPage");

					if (!showShellPage.booleanValue() && props.get("forbiddenPage") != null)
						pageName = props.get("forbiddenPage");

					return this.pageService.read(pageName.toString(), object.getAppCode(), clientCode);
				},

				(ca, ssp, shellPage) -> this.fillerService.read(object.getAppCode(), object.getAppCode(), clientCode),

				(ca, ssp, shellPage, filler) -> {

					if (shellPage == null) {

						if (filler == null)
							return Mono.just(new ObjectWithUniqueID<>(object, id));

						object.getProperties()
								.put("fillerValues", filler.getObject().getValues());
						return Mono.just(
								new ObjectWithUniqueID<>(object, filler == null ? id : id + filler.getUniqueId()));
					}

					StringBuilder sb = new StringBuilder(id);

					if (filler != null) {
						sb.append(filler.getUniqueId());
						object.getProperties()
								.put("fillerValues", filler.getObject().getValues());
					}

					sb.append(shellPage.getUniqueId());
					object.getProperties()
							.put("shellPageDefinition", shellPage.getObject());

					return Mono.just(
							new ObjectWithUniqueID<>(object, sb.toString()));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.applyChange"));
	}
}
