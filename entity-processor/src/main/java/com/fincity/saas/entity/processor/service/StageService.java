package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.StageDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.StageType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.StageReorderRequest;
import com.fincity.saas.entity.processor.model.request.StageRequest;
import com.fincity.saas.entity.processor.model.response.BaseValueResponse;
import com.fincity.saas.entity.processor.service.base.BaseValueService;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
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
    public Mono<Stage> applyOrder(Stage entity, ProcessorAccess access) {

        if (entity.isChild()) return Mono.just(entity);

        if (entity.getOrder() != null)
            return this.dao
                    .existsByOrder(
                            access.getAppCode(),
                            access.getClientCode(),
                            entity.getProductTemplateId(),
                            entity.getOrder(),
                            entity.getId())
                    .flatMap(exists -> {
                        if (Boolean.FALSE.equals(exists)) return Mono.just(entity);

                        return this.dao
                                .getAllValuesFlux(
                                        access.getAppCode(),
                                        access.getClientCode(),
                                        null,
                                        entity.getProductTemplateId(),
                                        true)
                                .filter(s -> {
                                    Integer order = s.getOrder();
                                    return order != null
                                            && order >= entity.getOrder()
                                            && !s.getId().equals(entity.getId());
                                })
                                .map(s -> s.setOrder(s.getOrder() + 1))
                                .flatMap(this::updateInternal)
                                .then(Mono.just(entity));
                    })
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.applyOrder"));

        return this.getNewOrder(entity, access)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.applyOrder"));
    }

    private Mono<Stage> getNewOrder(Stage entity, ProcessorAccess access) {
        return FlatMapUtil.flatMapMonoWithNull(
                        () -> this.getLatestStageByOrder(
                                access.getAppCode(), access.getClientCode(), entity.getProductTemplateId()),
                        latestStage -> {
                            if (latestStage == null) return Mono.just(entity.setOrder(1));

                            return Mono.just(entity.setOrder(latestStage.getOrder() + 1));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getNewOrder"));
    }

    public Mono<List<BaseValueResponse<Stage>>> getAllValuesInOrder(
            Platform platform, StageType stageType, ULong productTemplateId, ULong parentId) {
        return super.getAllValuesInOrder(platform, productTemplateId, parentId)
                .map(stages -> {
                    if (stageType == null) return stages;
                    return stages.stream()
                            .filter(stage -> stage.getParent().getStageType().equals(stageType))
                            .toList();
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getAllValuesInOrder"));
    }

    public Mono<List<BaseValueResponse<Stage>>> getAllValues(
            Platform platform, StageType stageType, ULong productTemplateId, ULong parentId) {
        return super.getAllValues(platform, productTemplateId, parentId)
                .map(stages -> {
                    if (stageType == null) return stages;
                    return stages.stream()
                            .filter(stage -> stage.getParent().getStageType().equals(stageType))
                            .toList();
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getAllValues"));
    }

    public Mono<BaseValueResponse<Stage>> create(StageRequest stageRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.productTemplateService.checkAndUpdateIdentityWithAccess(
                                access, stageRequest.getProductTemplateId()),
                        (access, productTemplateId) -> {
                            stageRequest.setProductTemplateId(productTemplateId);

                            if (stageRequest.getId() != null
                                    && stageRequest.getId().getId() != null) {
                                return super.readIdentityWithAccess(access, stageRequest.getId())
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
                        (access, productTemplateId, parentStage) -> stageRequest.getChildren() != null
                                ? this.updateOrCreateChildren(
                                        access, productTemplateId, stageRequest.getChildren(), parentStage)
                                : Mono.just(Tuples.of(parentStage, List.of())))
                .map(tuple -> new BaseValueResponse<>(tuple.getT1(), tuple.getT2()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.create[StageRequest]"));
    }

    private Mono<Tuple2<Stage, List<Stage>>> updateOrCreateChildren(
            ProcessorAccess access, Identity productTemplateId, Map<Integer, StageRequest> children, Stage parent) {

        if (children == null || children.isEmpty())
            return FlatMapUtil.flatMapMono(
                            () -> super.getValue(
                                    parent.getAppCode(),
                                    parent.getClientCode(),
                                    parent.getPlatform(),
                                    productTemplateId.getULongId(),
                                    parent.getId()),
                            valueEntry -> super.deleteMultiple(valueEntry.getValue()),
                            (valueEntry, deleted) -> this.evictCache(parent)
                                    .flatMap(evicted -> Mono.just(Tuples.of(parent, List.<Stage>of()))))
                    .defaultIfEmpty(Tuples.of(parent, List.of()))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.updateOrCreateChildren"));

        return FlatMapUtil.flatMapMono(
                        () -> super.getValue(
                                parent.getAppCode(),
                                parent.getClientCode(),
                                parent.getPlatform(),
                                productTemplateId.getULongId(),
                                parent.getId()),
                        valueEntry -> {
                            Map<ULong, Stage> existingChildrenMap = valueEntry.getValue().stream()
                                    .collect(Collectors.toMap(Stage::getId, Function.identity()));

                            return Flux.fromIterable(children.entrySet())
                                    .flatMap(entry -> {
                                        StageRequest childRequest =
                                                entry.getValue().setProductTemplateId(productTemplateId);
                                        Integer order = entry.getKey();

                                        if (childRequest.getId() != null
                                                && childRequest.getId().getId() != null) {
                                            ULong childId = childRequest.getId().getULongId();
                                            Stage existingChild = existingChildrenMap.get(childId);

                                            if (existingChild != null) {
                                                existingChildrenMap.remove(childId);

                                                existingChild
                                                        .setName(childRequest.getName())
                                                        .setDescription(childRequest.getDescription())
                                                        .setIsSuccess(childRequest.getIsSuccess())
                                                        .setIsFailure(childRequest.getIsFailure())
                                                        .setOrder(order);

                                                return super.updateInternal(access, existingChild);
                                            }
                                        }

                                        return super.createChild(
                                                access,
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
                                        if (!existingChildrenMap.isEmpty())
                                            return super.deleteMultiple(existingChildrenMap.values())
                                                    .flatMap(deleted -> this.evictCache(parent))
                                                    .then(Mono.just(Tuples.of(parent, updatedChildren)));
                                        return this.evictCache(parent)
                                                .flatMap(evicted -> Mono.just(Tuples.of(parent, updatedChildren)));
                                    });
                        })
                .defaultIfEmpty(Tuples.of(parent, List.of()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.updateOrCreateChildren"));
    }

    public Mono<Stage> getLatestStageByOrder(String appCode, String clientCode, ULong productTemplateId) {
        return super.getAllValuesInOrderInternal(appCode, clientCode, null, productTemplateId)
                .map(NavigableMap::lastKey)
                .switchIfEmpty(Mono.empty())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getLatestStageByOrder"));
    }

    public Mono<Stage> getFirstStage(String appCode, String clientCode, ULong productTemplateId) {
        return super.getAllValuesInOrder(appCode, clientCode, null, productTemplateId)
                .map(NavigableMap::firstKey)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getFirstStage"));
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
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getFirstStatus"));
    }

    public Mono<ULong> getStage(String appCode, String clientCode, ULong productTemplateId, ULong stageId) {
        return super.getAllValueIds(appCode, clientCode, null, productTemplateId)
                .flatMap(stageIdsInternal -> {
                    if (stageIdsInternal == null || stageIdsInternal.isEmpty()) return Mono.empty();
                    if (!stageIdsInternal.contains(stageId)) return Mono.empty();
                    return Mono.just(stageId);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.getStage"));
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

    public Mono<List<Stage>> reorderStages(StageReorderRequest reorderRequest) {

        if (!reorderRequest.isValidOrder())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    "Valid order is required for stage reordering.");

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.productTemplateService.checkAndUpdateIdentityWithAccess(
                                access, reorderRequest.getProductTemplateId()),
                        (access, productTemplateId) -> Flux.fromIterable(
                                        reorderRequest.getStageOrders().entrySet())
                                .flatMap(entry -> this.checkAndUpdateIdentityWithAccess(access, entry.getKey())
                                        .map(identity -> Tuples.of(identity.getULongId(), entry.getValue())))
                                .collectMap(Tuple2::getT1, Tuple2::getT2),
                        (access, productTemplateId, requestStageIds) -> this.getAllValues(
                                access.getAppCode(), access.getClientCode(), null, productTemplateId.getULongId()),
                        (access, productTemplateId, requestStageIds, allStages) -> {
                            Map<ULong, Stage> parentStageMap = BaseUpdatableDto.toIdMap(allStages.keySet());

                            if (!requestStageIds.keySet().equals(parentStageMap.keySet()))
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        "All parent stages must be provided in the request");

                            return Flux.fromIterable(requestStageIds.entrySet())
                                    .flatMap(entry -> {
                                        Stage stage = parentStageMap.get(entry.getKey());
                                        stage.setOrder(entry.getValue());
                                        return this.updateInternal(stage);
                                    })
                                    .collectList();
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "StageService.reorderStages"));
    }
}
