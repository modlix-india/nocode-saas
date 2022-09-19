package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.repository.ApplicationRepository;

import reactor.core.publisher.Mono;

@Service
public class ApplicationService extends AbstractUIServcie<Application, ApplicationRepository> {

	protected ApplicationService() {
		super(Application.class);
	}

	@PreAuthorize("hasPermission('Authorities.Application_CREATE')")
	@Override
	public Mono<Application> create(Application entity) {

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
				        return Mono.empty();

			        return Mono.just(existing.setProperties(entity.getProperties()));
		        });
	}
	
}
