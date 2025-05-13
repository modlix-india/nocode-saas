package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.SourceDAO;
import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSourcesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.SourceRequest;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SourceService extends BaseValueService<EntityProcessorSourcesRecord, Source, SourceDAO> {

    private static final String SOURCE_CACHE = "source";

    @Override
    protected String getCacheName() {
        return SOURCE_CACHE;
    }

    public Mono<Source> create(SourceRequest sourceRequest) {
        return FlatMapUtil.flatMapMono(
                () -> super.valueTemplateService.checkAndUpdateIdentity(sourceRequest.getValueTemplateId()),
                valueTemplateId -> super.create(Source.ofParent(sourceRequest.setValueTemplateId(valueTemplateId))),
                (valueTemplateId, parentSource) -> sourceRequest.getChildren() != null
                        ? this.createChildren(valueTemplateId, sourceRequest.getChildren(), parentSource)
                        : Mono.just(parentSource));
    }

    private Mono<Source> createChildren(Identity valueTemplateId, Map<Integer, SourceRequest> children, Source parent) {

        if (children == null || children.isEmpty()) return Mono.just(parent);

        return Flux.fromIterable(children.values())
                .flatMap(childRequest -> super.createChild(
                        Source.ofChild(childRequest.setValueTemplateId(valueTemplateId), parent), parent))
                .collectList()
                .then(Mono.just(parent));
    }
}
