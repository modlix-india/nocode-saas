package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractAppbasedOverridableDataService;
import com.fincity.saas.commons.mongo.service.CoreMessageResourceService;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.repository.PageRepository;

import reactor.core.publisher.Mono;

@Service
public class PageService extends AbstractAppbasedOverridableDataService<Page, PageRepository> {

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
				                CoreMessageResourceService.VERSION_MISMATCH);

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

	@Override
	protected Mono<Page> applyChange(Page object) {

		return flatMapMono(SecurityContextUtil::getUsersContextAuthentication, ca -> {

			object.setComponentDefinition(object.getComponentDefinition()
			        .entrySet()
			        .stream()
			        .filter(c -> Objects.nonNull(c.getValue()
			                .getPermission()))
			        .filter(c -> !SecurityContextUtil.hasAuthority(c.getValue()
			                .getPermission(), ca.getAuthorities()))
			        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

			return Mono.just(object);
		}).defaultIfEmpty(object);

	}
}
