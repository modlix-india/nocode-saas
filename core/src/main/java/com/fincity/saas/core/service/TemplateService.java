package com.fincity.saas.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.repository.TemplateRepository;

import reactor.core.publisher.Mono;

@Service
public class TemplateService extends AbstractOverridableDataService<Template, TemplateRepository> {

	protected TemplateService() {
		super(Template.class);
	}

	@Override
	protected Mono<Template> updatableEntity(Template entity) {
		
		return flatMapMono(

		        () -> this.read(entity.getId()),

		        existing ->
				{
			        if (existing.getVersion() != entity.getVersion())
				        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
				                AbstractMongoMessageResourceService.VERSION_MISMATCH);

			        existing.setTemplateParts(entity.getTemplateParts());
			        existing.setResources(entity.getResources());
			        existing.setDefaultLanguage(entity.getDefaultLanguage());
			        existing.setTemplateType(entity.getTemplateType());
			        existing.setToExpression(entity.getToExpression());
			        existing.setFromExpression(entity.getFromExpression());
			        existing.setLanguageExpression(entity.getLanguageExpression());

			        existing.setVersion(existing.getVersion() + 1);

			        return Mono.just(existing);
		        });
	}

}
