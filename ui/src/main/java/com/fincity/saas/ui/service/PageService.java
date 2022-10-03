package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.repository.PageRepository;

import reactor.core.publisher.Mono;

@Service
public class PageService extends AbstractUIServcie<Page, PageRepository> {

	public PageService() {
		super(Page.class);
	}

	@Override
	protected Mono<Page> updatableEntity(Page entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                UIMessageResourceService.VERSION_MISMATCH);

			        existing.setDevice(entity.getDevice())
			                .setTranslations(entity.getTranslations())
			                .setProperties(entity.getProperties())
			                .setEventFunctions(entity.getEventFunctions())
			                .setRootComponent(entity.getRootComponent())
			                .setComponentDefinition(entity.getComponentDefinition());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
}
