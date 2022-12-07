package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataServcie;
import com.fincity.saas.commons.mongo.service.CoreMessageResourceService;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;

import reactor.core.publisher.Mono;

@Service
public class ApplicationService extends AbstractOverridableDataServcie<Application, ApplicationRepository> {

	@Autowired
	private PageService pageService;

	protected ApplicationService() {
		super(Application.class);
	}

	@Override
	protected Mono<Application> updatableEntity(Application entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                CoreMessageResourceService.VERSION_MISMATCH);

			        existing.setProperties(entity.getProperties())
			                .setTranslations(entity.getTranslations())
			                .setLanguages(entity.getLanguages());

			        existing.setMessage(entity.getMessage());
			        existing.setDefaultLanguage(entity.getDefaultLanguage());
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

	@Override
	protected Mono<Application> applyChange(Application object) {

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

			        return this.pageService.read(pageName.toString(), object.getAppCode(),
			                object.getClientCode());

		        },

		        (ca, ssp, shellPage) ->
				{
			        object.getProperties()
			                .put("shellPageDefinition", shellPage);

			        return Mono.just(object);
		        });
	}
}
