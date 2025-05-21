package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.BaseValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.Map;
import java.util.TreeMap;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

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

    @Override
    public Mono<Stage> applyOrder(Stage entity, Tuple3<String, String, ULong> accessInfo) {

        if (entity.isChild()) return Mono.just(entity);

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.getLatestStageByOrder(
                        accessInfo.getT1(), accessInfo.getT2(), entity.getValueTemplateId(), entity.getPlatform()),
                latestStage -> {
                    if (latestStage == null) return Mono.just(entity.setOrder(1));

                    return Mono.just(entity.setOrder(latestStage.getOrder() + 1));
                });
    }

    public Mono<Stage> create(StageRequest stageRequest) {
        return FlatMapUtil.flatMapMono(
                () -> super.productTemplateService.checkAndUpdateIdentity(stageRequest.getValueTemplateId()),
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
                                parent.getStageType(),
                                parent),
                        parent))
                .collectList()
                .then(Mono.just(parent));
    }

    public Mono<BaseValue> getLatestStageByOrder(
            String appCode, String clientCode, ULong valueTemplateId, Platform platform) {
        return super.getAllValuesInOrder(appCode, clientCode, platform, valueTemplateId)
                .map(TreeMap::lastKey);
    }

    public Mono<BaseValue> getFirstStage(String appCode, String clientCode, ULong valueTemplateId) {
        return super.getAllValuesInOrder(appCode, clientCode, null, valueTemplateId)
                .map(TreeMap::firstKey);
    }

    public Mono<BaseValue> getFirstStatus(String appCode, String clientCode, ULong valueTemplateId, ULong stageId) {
        return super.getAllValuesInOrder(appCode, clientCode, null, valueTemplateId)
                .flatMap(treeMap -> {
                    BaseValue stage = treeMap.keySet().stream()
                            .filter(key -> key.getId().equals(stageId))
                            .findFirst()
                            .orElse(null);

                    if (stage == null || !treeMap.containsKey(stage)) return Mono.empty();

                    return Mono.justOrEmpty(treeMap.get(stage).first());
                });
    }

    private Mono<Boolean> updateOrder(Map<Integer, Identity> stageMap) {
        return FlatMapUtil.flatMapMono(super::hasAccess, hasAccess -> Flux.fromIterable(stageMap.entrySet())
                .flatMap(entry ->
                        super.readIdentityInternal(entry.getValue()).map(stage -> stage.setOrder(entry.getKey())))
                .flatMap(super::updateInternal)
                .then(Mono.just(Boolean.TRUE)));
    }
}
