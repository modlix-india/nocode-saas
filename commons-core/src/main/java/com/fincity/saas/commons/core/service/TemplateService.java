package com.fincity.saas.commons.core.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.repository.TemplateRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mongo.service.AbstractOverridableDataService;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TemplateService extends AbstractOverridableDataService<Template, TemplateRepository> {

    protected TemplateService() {
        super(Template.class);
    }

    @Override
    protected Mono<Template> updatableEntity(Template entity) {

        return flatMapMono(() -> this.read(entity.getId()), existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
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
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TemplateService.updatableEntity"));
    }
}
