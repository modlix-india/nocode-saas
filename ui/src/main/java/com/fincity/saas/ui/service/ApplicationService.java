package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ApplicationService extends AbstractUIServcie<Application, ApplicationRepository> {

	@Autowired
	private PageService pageService;

	protected ApplicationService() {
		super(Application.class);
	}

	@Override
	public Mono<Application> create(Application entity) {

		entity.setApplicationName(entity.getName());

		return SecurityContextUtil.getUsersContextAuthentication()
		        .filter(ContextAuthentication::isSystemClient)
		        .flatMap(ca -> super.create(entity));
	}

	@Override
	protected Mono<Application> updatableEntity(Application entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                UIMessageResourceService.VERSION_MISMATCH);

			        existing.setProperties(entity.getProperties())
			                .setTranslations(entity.getTranslations())
			                .setLanguages(entity.getLanguages());

			        existing.setMessage(entity.getMessage());
			        existing.setDefaultLanguage(entity.getDefaultLanguage());
			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

	@SuppressWarnings("unchecked")
	public Mono<List<String>> getAppNClientCodes(String scheme, String host, String port) {
		return this.repo.findAll()
		        .flatMap(a ->
				{
			        if (a.getProperties() == null)
				        return Flux.empty();

			        if (!a.getProperties()
			                .containsKey("urlList"))
				        return Flux.empty();

			        try {
				        List<String> x = (List<String>) a.getProperties()
				                .get("urlPatterns");

				        return Flux.fromIterable(x)
				                .map(e -> new ClientUrlPattern(a.getName(), a.getClientCode(), e));

			        } catch (Exception ex) {
				        return Flux.empty();
			        }
		        })
		        .map(ClientUrlPattern::makeHostnPort)
		        .filter(e -> e.isValidClientURLPattern(scheme, host, port))
		        .take(1)
		        .singleOrEmpty()
		        .map(e -> List.of(e.getIdentifier(), e.getClientCode()))
		        .defaultIfEmpty(List.of("", ""))
		        .log();
	}

	@Override
	protected Mono<Application> applyChange(Application object) {

		if (object == null)
			return Mono.empty();

		return FlatMapUtil.flatMapMono(

		        () ->
				{

			        if (object.getProperties() == null || object.getProperties()
			                .get("shellPage") == null)
				        return Mono.empty();

			        return this.pageService.read(object.getProperties()
			                .get("shellPage")
			                .toString(), object.getApplicationName(), object.getClientCode());
		        },

		        shellPage ->
				{

			        object.getProperties()
			                .put("shellPageDefinition", shellPage);
			        return Mono.just(object);
		        });
	}
}
