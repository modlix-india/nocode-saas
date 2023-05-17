package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.ui.document.StyleTheme;
import com.fincity.saas.ui.repository.StyleThemeRepository;

import reactor.core.publisher.Mono;

@Service
public class StyleThemeService extends AbstractOverridableDataService<StyleTheme, StyleThemeRepository> {

	public StyleThemeService() {
		super(StyleTheme.class);
	}

	@Override
	protected Mono<StyleTheme> updatableEntity(StyleTheme entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setVariables(entity.getVariables());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
	
	@Override
	public String getObjectName() {
		return "Theme";
	}
}
