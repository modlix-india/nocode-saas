package com.fincity.saas.entity.processor.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.service.base.BaseValueService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StageService extends BaseValueService<EntityProcessorStagesRecord, Stage, StageDAO> {

    private static final String STAGE_CACHE = "stage";

    @Override
    protected String getCacheName() {
        return STAGE_CACHE;
    }

    public Mono<Stage> create(StageRequest stageRequest) {
        return FlatMapUtil.flatMapMono(
                () -> super.create(Stage.ofParent(stageRequest)),
                parentStage -> Flux.fromIterable(stageRequest.getChildren().entrySet())
                        .flatMap(childEntry -> super.createChild(
                                Stage.ofChild(childEntry.getValue(), childEntry.getKey(), parentStage.getId()),
                                parentStage))
                        .collectList()
                        .then(Mono.just(parentStage)));
    }

    private Mono<Boolean> updateOrder(Map<Integer, Identity> stageMap) {
        return FlatMapUtil.flatMapMono(super::hasAccess, hasAccess -> Flux.fromIterable(stageMap.entrySet())
                .flatMap(entry -> super.readIdentity(entry.getValue())
                        .map(stage -> stage.setOrder(entry.getKey())))
                .flatMap(super::updateInternal)
                .then(Mono.just(Boolean.TRUE)));
    }
}
