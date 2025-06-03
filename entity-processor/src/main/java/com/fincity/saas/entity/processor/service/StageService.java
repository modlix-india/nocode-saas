package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
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
                        accessInfo.getT1(), accessInfo.getT2(), entity.getProductTemplateId(), entity.getPlatform()),
                latestStage -> {
                    if (latestStage == null) return Mono.just(entity.setOrder(1));

                    return Mono.just(entity.setOrder(latestStage.getOrder() + 1));
                });
    }

    public Mono<Stage> create(StageRequest stageRequest) {
        return FlatMapUtil.flatMapMono(
                () -> super.productTemplateService.checkAndUpdateIdentity(stageRequest.getProductTemplateId()),
                productTemplateId -> super.create(Stage.ofParent(stageRequest.setProductTemplateId(productTemplateId))),
                (productTemplateId, parentStage) -> stageRequest.getChildren() != null
                        ? this.createChildren(productTemplateId, stageRequest.getChildren(), parentStage)
                        : Mono.just(parentStage));
    }

    private Mono<Stage> createChildren(Identity productTemplateId, Map<Integer, StageRequest> children, Stage parent) {

        if (children == null || children.isEmpty()) return Mono.just(parent);

        return Flux.fromIterable(children.entrySet())
                .flatMap(childRequest -> super.createChild(
                        Stage.ofChild(
                                childRequest.getValue().setProductTemplateId(productTemplateId),
                                childRequest.getKey(),
                                parent.getPlatform(),
                                parent.getStageType(),
                                parent),
                        parent))
                .collectList()
                .then(Mono.just(parent));
    }

    public Mono<Stage> getLatestStageByOrder(
            String appCode, String clientCode, ULong productTemplateId, Platform platform) {
        return super.getAllValuesInOrderInternal(appCode, clientCode, platform, productTemplateId)
                .map(NavigableMap::lastKey)
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Stage> getFirstStage(String appCode, String clientCode, ULong productTemplateId) {
        return super.getAllValuesInOrder(appCode, clientCode, null, productTemplateId)
                .map(NavigableMap::firstKey);
    }

    public Mono<Stage> getFirstStatus(String appCode, String clientCode, ULong productTemplateId, ULong stageId) {
        return super.getAllValuesInOrder(appCode, clientCode, null, productTemplateId)
                .flatMap(navigableMap -> {
                    Stage stage = navigableMap.keySet().stream()
                            .filter(key -> key.getId().equals(stageId))
                            .findFirst()
                            .orElse(null);

                    if (stage == null || !navigableMap.containsKey(stage)) return Mono.empty();

                    if (navigableMap.get(stage) == null
                            || navigableMap.get(stage).isEmpty()) return Mono.empty();

                    return Mono.justOrEmpty(navigableMap.get(stage).first());
                });
    }

    public Mono<ULong> getStage(String appCode, String clientCode, ULong productTemplateId, ULong stageId) {
        return super.getAllValueIds(appCode, clientCode, null, productTemplateId)
                .flatMap(stageIdsInternal -> {
                    if (stageIdsInternal == null || stageIdsInternal.isEmpty()) return Mono.empty();
                    if (!stageIdsInternal.contains(stageId)) return Mono.empty();
                    return Mono.just(stageId);
                });
    }

    public Mono<Set<ULong>> getAllStages(
            String appCode, String clientCode, ULong productTemplateId, ULong... stageIds) {
        return super.getAllValueIds(appCode, clientCode, null, productTemplateId)
                .flatMap(stageIdsInternal -> {
                    if (stageIdsInternal == null || stageIdsInternal.isEmpty()) return Mono.just(Set.of());

                    if (stageIds == null || stageIds.length == 0) return Mono.just(stageIdsInternal);

                    if (!stageIdsInternal.containsAll(List.of(stageIds))) return Mono.just(Set.of());

                    stageIdsInternal.retainAll(List.of(stageIds));

                    return Mono.just(stageIdsInternal);
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
