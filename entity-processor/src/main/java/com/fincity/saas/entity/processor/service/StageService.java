package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.base.BaseResponse;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.model.response.BaseValueResponse;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

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

    public Mono<List<BaseValueResponse<Stage>>> getAllValuesInOrder(
            Platform platform, StageType stageType, ULong productTemplateId) {
        return super.getAllValuesInOrder(platform, productTemplateId).map(stages -> {
            if (stageType == null) return stages;
            return stages.stream()
                    .filter(stage -> stage.getParent().getStageType().equals(stageType))
                    .toList();
        });
    }

    public Mono<List<BaseValueResponse<Stage>>> getAllValues(
            Platform platform, StageType stageType, ULong productTemplateId) {
        return super.getAllValues(platform, productTemplateId).map(stages -> {
            if (stageType == null) return stages;
            return stages.stream()
                    .filter(stage -> stage.getParent().getStageType().equals(stageType))
                    .toList();
        });
    }

    public Mono<BaseValueResponse<Stage>> create(StageRequest stageRequest) {
        return FlatMapUtil.flatMapMono(
                        () -> super.productTemplateService.checkAndUpdateIdentity(stageRequest.getProductTemplateId()),
                        productTemplateId -> {
                            stageRequest.setProductTemplateId(productTemplateId);

                            if (stageRequest.getId() != null
                                    && stageRequest.getId().getId() != null) {
                                return super.readIdentity(stageRequest.getId())
                                        .flatMap(existingStage -> {
                                            existingStage
                                                    .setName(stageRequest.getName())
                                                    .setDescription(stageRequest.getDescription())
                                                    .setStageType(stageRequest.getStageType())
                                                    .setIsSuccess(stageRequest.getIsSuccess())
                                                    .setIsFailure(stageRequest.getIsFailure())
                                                    .setPlatform(stageRequest.getPlatform());

                                            return super.update(existingStage);
                                        })
                                        .switchIfEmpty(super.create(Stage.ofParent(stageRequest)));
                            } else {
                                return super.create(Stage.ofParent(stageRequest));
                            }
                        },
                        (productTemplateId, parentStage) -> stageRequest.getChildren() != null
                                ? this.updateOrCreateChildren(
                                        productTemplateId, stageRequest.getChildren(), parentStage)
                                : Mono.just(Tuples.of(parentStage, List.<Stage>of())))
                .map(tuple -> new BaseValueResponse<>(tuple.getT1(), tuple.getT2()));
    }

    private Mono<Tuple2<Stage, List<Stage>>> updateOrCreateChildren(
            Identity productTemplateId, Map<Integer, StageRequest> children, Stage parent) {

        if (children == null || children.isEmpty()) {
            return super.getAllValues(
                            parent.getAppCode(),
                            parent.getClientCode(),
                            parent.getPlatform(),
                            ULongUtil.valueOf(productTemplateId.getId()))
                    .flatMap(valueMap -> {
                        Set<Stage> existingChildren = valueMap.getOrDefault(parent, Set.of());

                        return Flux.fromIterable(existingChildren)
                                .flatMap(child -> super.delete(child.getId()))
                                .then(Mono.just(Tuples.of(parent, List.<Stage>of())));
                    })
                    .defaultIfEmpty(Tuples.of(parent, List.<Stage>of()));
        }

        return super.getAllValues(
                        parent.getAppCode(),
                        parent.getClientCode(),
                        parent.getPlatform(),
                        ULongUtil.valueOf(productTemplateId.getId()))
                .flatMap(valueMap -> {
                    Set<Stage> existingChildren = valueMap.getOrDefault(parent, Set.of());
                    Map<ULong, Stage> existingChildrenMap =
                            existingChildren.stream().collect(Collectors.toMap(Stage::getId, child -> child));

                    return Flux.fromIterable(children.entrySet())
                            .flatMap(entry -> {
                                StageRequest childRequest = entry.getValue().setProductTemplateId(productTemplateId);
                                Integer order = entry.getKey();

                                if (childRequest.getId() != null
                                        && childRequest.getId().getId() != null) {
                                    ULong childId = ULongUtil.valueOf(
                                            childRequest.getId().getId());
                                    Stage existingChild = existingChildrenMap.get(childId);

                                    if (existingChild != null) {
                                        existingChildrenMap.remove(childId);

                                        existingChild
                                                .setName(childRequest.getName())
                                                .setDescription(childRequest.getDescription())
                                                .setIsSuccess(childRequest.getIsSuccess())
                                                .setIsFailure(childRequest.getIsFailure())
                                                .setOrder(order);

                                        return super.update(existingChild);
                                    }
                                }

                                return super.createChild(
                                        Stage.ofChild(
                                                childRequest,
                                                order,
                                                parent.getPlatform(),
                                                parent.getStageType(),
                                                parent),
                                        parent);
                            })
                            .collectList()
                            .flatMap(updatedChildren -> {
                                if (!existingChildrenMap.isEmpty()) {
                                    return Flux.fromIterable(existingChildrenMap.values())
                                            .flatMap(child -> super.delete(child.getId()))
                                            .then(Mono.just(Tuples.of(parent, updatedChildren)));
                                }
                                return Mono.just(Tuples.of(parent, updatedChildren));
                            });
                })
                .defaultIfEmpty(Tuples.of(parent, List.<Stage>of()));
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
