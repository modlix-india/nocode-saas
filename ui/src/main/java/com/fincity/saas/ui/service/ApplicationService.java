package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
public class ApplicationService extends AbstractOverridableDataService<Application, ApplicationRepository> {

	private static final String PROPERTIES = "Properties : ";
	@Autowired
	private PageService pageService;

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
	public Mono<Application> update(Application entity) {

		return super.update(entity).flatMap(
		        e -> cacheService.evict(IndexHTMLService.CACHE_NAME_INDEX, e.getAppCode(), "-", e.getClientCode())
		                .flatMap(x -> cacheService.evict(this.getCacheName(), PROPERTIES, e.getName(), "-",
		                        e.getAppCode(), "-", e.getClientCode()))
		                .map(x -> e));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		return this.read(id)
		        .flatMap(e -> super.delete(id).flatMap(x -> cacheService
		                .evict(IndexHTMLService.CACHE_NAME_INDEX, e.getAppCode(), "-", e.getClientCode())
		                .flatMap(y -> cacheService.evict(this.getCacheName(), PROPERTIES, e.getName(), "-",
		                        e.getAppCode(), "-", e.getClientCode()))
		                .map(y -> x)));
	}

	@Override
	protected Mono<Application> updatableEntity(Application entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setProperties(entity.getProperties())
			                .setTranslations(entity.getTranslations())
			                .setLanguages(entity.getLanguages())
			                .setPermission(entity.getPermission());

			        existing.setMessage(entity.getMessage());
			        existing.setDefaultLanguage(entity.getDefaultLanguage());
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

	public Mono<Map<String, Object>> readProperties(String name, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> cacheService.makeKey(PROPERTIES, name, "-", appCode, "-", clientCode),

		        key -> cacheService.get(this.getCacheName(), key)
		                .map(this.pojoClass::cast),

		        (key, cApp) -> Mono.justOrEmpty(cApp)
		                .switchIfEmpty(Mono.defer(() -> readIfExistsInBase(name, appCode, clientCode))),

		        (key, cApp, dbApp) -> Mono.justOrEmpty(dbApp)
		                .flatMap(da -> this.readInternal(da.getId())
		                        .map(this.pojoClass::cast)),

		        (key, cApp, dbApp, mergedApp) ->
				{

			        if (cApp == null && mergedApp == null)
				        return Mono.empty();

			        try {
				        return Mono.just(this.pojoClass.getConstructor(this.pojoClass)
				                .newInstance(cApp != null ? cApp : mergedApp));
			        } catch (Exception e) {

				        return this.messageResourceService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
				                AbstractMongoMessageResourceService.UNABLE_TO_CREAT_OBJECT, this.getObjectName());
			        }
		        },

		        (key, cApp, dbApp, mergedApp, clonedApp) ->
				{

			        if (clonedApp == null)
				        return Mono.empty();

			        if (cApp == null && mergedApp != null) {
				        cacheService.put(this.getCacheName(), mergedApp, key);
			        }

			        return Mono.justOrEmpty(clonedApp.getProperties());
		        })
		        .defaultIfEmpty(Map.of());
	}

	@Override
	protected Mono<Application> applyChange(String name, String appCode, String clientCode, Application object) {

		if (object == null)
			return Mono.empty();

		return FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(ca == null || object.getPermission() == null)
		                .map(e -> e || SecurityContextUtil.hasAuthority(object.getPermission(), ca.getAuthorities())),

		        (ca, showShellPage) ->
				{

			        Map<String, Object> props = object.getProperties();

			        if (props == null || props.get("shellPage") == null)
				        return Mono.empty();

			        Object pageName = props.get("shellPage");

			        if (!showShellPage.booleanValue() && props.get("forbiddenPage") != null)
				        pageName = props.get("forbiddenPage");

			        return this.pageService.read(pageName.toString(), object.getAppCode(), clientCode);

		        },

		        (ca, ssp, shellPage) ->
				{
			        object.getProperties()
			                .put("shellPageDefinition", shellPage);

			        return Mono.just(object);
		        });
	}
}
