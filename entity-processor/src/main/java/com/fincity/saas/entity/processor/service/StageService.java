package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StageService extends BaseValueService<EntityProcessorStagesRecord, Stage, StageDAO> {

    private static final String STAGE_CACHE = "stage";

    @Override
    protected String getCacheName() {
        return STAGE_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.STAGE;
    }

    public Mono<Stage> create(StageRequest stageRequest) {
        return FlatMapUtil.flatMapMono(
                () -> super.valueTemplateService.checkAndUpdateIdentity(stageRequest.getValueTemplateId()),
                valueTemplateId -> super.create(Stage.ofParent(stageRequest.setValueTemplateId(valueTemplateId))),
                (valueTemplateId, parentStage) -> stageRequest.getChildren() != null
                        ? this.createChildren(valueTemplateId, stageRequest.getChildren(), parentStage)
                        : Mono.just(parentStage));
    }

    private Mono<Stage> createChildren(Identity valueTemplateId, Map<Integer, StageRequest> children, Stage parent) {

        if (children == null || children.isEmpty()) return Mono.just(parent);

        return Flux.fromIterable(children.entrySet())
                .flatMap(childRequest -> super.createChild(
                        Stage.ofChild(
                                childRequest.getValue().setValueTemplateId(valueTemplateId),
                                childRequest.getKey(),
                                parent.getPlatform(),
                                parent),
                        parent))
                .collectList()
                .then(Mono.just(parent));
    }

    private Mono<Boolean> updateOrder(Map<Integer, Identity> stageMap) {
        return FlatMapUtil.flatMapMono(super::hasAccess, hasAccess -> Flux.fromIterable(stageMap.entrySet())
                .flatMap(entry ->
                        super.readIdentityInternal(entry.getValue()).map(stage -> stage.setOrder(entry.getKey())))
                .flatMap(super::updateInternal)
                .then(Mono.just(Boolean.TRUE)));
    }
}
