package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.ui.document.StyleTheme;
import com.fincity.saas.ui.repository.StyleThemeRepository;
import com.fincity.saas.ui.util.DifferenceApplicator;
import com.fincity.saas.ui.util.DifferenceExtractor;

import reactor.core.publisher.Mono;

@Service
public class StyleThemeService extends AbstractAppbasedUIService<StyleTheme, StyleThemeRepository> {

	@Autowired
	private StyleService styleService;

	public StyleThemeService() {
		super(StyleTheme.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<StyleTheme> create(StyleTheme entity) {

		return flatMapMono(

		        () -> styleService.read(entity.getStyleName(), entity.getApplicationName(), entity.getClientCode()),

		        style -> DifferenceExtractor.extract(entity.getVariables(), style.getVariables()),

		        (style, vars) -> super.create(entity.setVariables((Map<String, Map<String, String>>) vars)));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<StyleTheme> read(String id) {

		return flatMapMono(

		        () -> super.read(id),

		        entity -> styleService.read(entity.getStyleName(), entity.getApplicationName(), entity.getClientCode()),

		        (entity, style) -> DifferenceApplicator.apply(entity.getVariables(), style.getVariables()),

		        (entity, style, vars) -> Mono.just(entity.setVariables((Map<String, Map<String, String>>) vars)));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<StyleTheme> readInternal(String id) {

		return flatMapMono(

		        () -> super.readInternal(id),

		        entity -> styleService.read(entity.getStyleName(), entity.getApplicationName(), entity.getClientCode()),

		        (entity, style) -> DifferenceApplicator.apply(entity.getVariables(), style.getVariables()),

		        (entity, style, vars) -> Mono.just(entity.setVariables((Map<String, Map<String, String>>) vars)));
	}

	@Override
	protected Mono<StyleTheme> updatableEntity(StyleTheme entity) {

		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                UIMessageResourceService.VERSION_MISMATCH);

			        existing.setVariables(entity.getVariables());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}
}
