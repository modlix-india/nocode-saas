package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.ui.document.Theme;
import com.fincity.saas.ui.repository.ThemeRepository;

import reactor.core.publisher.Mono;

@Service
public class ThemeService extends AbstractUIServcie<Theme, ThemeRepository> {

	protected ThemeService() {
		super(Theme.class);
	}

	@Override
	protected Mono<Theme> updatableEntity(Theme entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                UIMessageResourceService.VERSION_MISMATCH);

			        existing.setStyles(entity.getStyles())
			                .setVariables(entity.getVariables())
			                .setVariableGroups(entity.getVariableGroups());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

}
