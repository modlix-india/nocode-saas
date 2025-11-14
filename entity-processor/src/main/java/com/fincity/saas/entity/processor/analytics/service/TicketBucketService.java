package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.service.base.BaseAnalyticsService;
import com.fincity.saas.entity.processor.analytics.util.DatePair;
import com.fincity.saas.entity.processor.analytics.util.ReactivePaginationUtil;
import com.fincity.saas.entity.processor.analytics.util.ReportUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketBucketService extends BaseAnalyticsService<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO> {

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    public Mono<Page<StatusEntityCount>> getTicketPerAssignedUserStageCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> super.resolveAssignedUsers(access, f),
                        this.dao::getTicketPerAssignedUserStageCount,
                        sFilter -> sFilter.getBaseFieldData().getAssignedUsers(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStageCount"));
    }

    public Mono<List<StatusEntityCount>> getTicketPerCreatedByStageCount(
            ProcessorAccess access, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        access,
                        Boolean.TRUE,
                        filter,
                        this.dao::getTicketPerCreatedByStageCount,
                        sFilter -> sFilter.getBaseFieldData().getCreatedBys(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "TicketBucketService.getTicketPerCreatedByStageCount[ProcessorAccess, TicketBucketFilter]"));
    }

    public Flux<DateStatusCount> getTicketPerAssignedUserStageSourceDateCount(TicketBucketFilter filter) {
        return FlatMapUtil.flatMapFlux(
                        () -> super.hasAccess().flux(),
                        access -> resolveStages(access, filter).flux(),
                        (access, sFilter) -> this.dao
                                .getTicketPerAssignedUserStageSourceDateCount(access, sFilter)
                                .collectList()
                                .flux(),
                        (access, sFilter, perStageCount) -> ReportUtil.toDateStatusCounts(
                                DatePair.of(filter.getStartDate(), filter.getEndDate()),
                                filter.getTimePeriod(),
                                perStageCount,
                                sFilter.getFieldData().getStages(),
                                sFilter.isIncludeZero(),
                                sFilter.isIncludePercentage(),
                                sFilter.isIncludeTotal(),
                                sFilter.isIncludeNone()))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStageSourceDateCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerAssignedUserStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.FALSE,
                        filter,
                        access -> f -> super.resolveAssignedUsers(access, f),
                        this.dao::getTicketPerAssignedUserStatusCount,
                        sFilter -> sFilter.getBaseFieldData().getAssignedUsers(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerCreatedByStageCount(Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> super.resolveCreatedBys(access, f),
                        this.dao::getTicketPerCreatedByStageCount,
                        sFilter -> sFilter.getBaseFieldData().getCreatedBys(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerCreatedByStageCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerCreatedByStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.FALSE,
                        filter,
                        access -> f -> super.resolveCreatedBys(access, f),
                        this.dao::getTicketPerCreatedByStatusCount,
                        sFilter -> sFilter.getBaseFieldData().getCreatedBys(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerCreatedByStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerClientIdStageCount(Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> super.resolveClients(access, f),
                        this.dao::getTicketPerClientIdStageCount,
                        sFilter -> sFilter.getBaseFieldData().getClients(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerClientIdStageCount"));
    }

    public Mono<List<StatusEntityCount>> getTicketPerClientIdStageCount(
            ProcessorAccess access, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        access,
                        Boolean.TRUE,
                        filter,
                        this.dao::getTicketPerClientIdStageCount,
                        sFilter -> sFilter.getBaseFieldData().getClients(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "TicketBucketService.getTicketPerClientIdStageCount[ProcessorAccess, TicketBucketFilter]"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerClientIdStatusCount(Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.FALSE,
                        filter,
                        access -> f -> super.resolveClients(access, f),
                        this.dao::getTicketPerClientIdStatusCount,
                        sFilter -> sFilter.getBaseFieldData().getClients(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerClientIdStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerProjectStageCount(Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> this.resolveProducts(access, f),
                        this.dao::getTicketPerProjectStageCount,
                        sFilter -> sFilter.getFieldData().getProducts(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProjectStageCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerProjectStatusCount(Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.FALSE,
                        filter,
                        access -> f -> this.resolveProducts(access, f),
                        this.dao::getTicketPerProjectStatusCount,
                        sFilter -> sFilter.getFieldData().getProducts(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProjectStatusCount"));
    }

    private Mono<Page<StatusEntityCount>> getTicketCountByGroupAndJoin(
            Pageable pageable,
            boolean byStage,
            TicketBucketFilter filter,
            Function<ProcessorAccess, Function<TicketBucketFilter, Mono<TicketBucketFilter>>> resolver,
            BiFunction<ProcessorAccess, TicketBucketFilter, Flux<PerValueCount>> daoMethod,
            Function<TicketBucketFilter, List<IdAndValue<ULong, String>>> baseFieldExtractor,
            Function<TicketBucketFilter, List<IdAndValue<ULong, String>>> stageExtractor) {

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> resolver.apply(access).apply(filter),
                        (access, resolvedFilter) -> this.getTicketCountByGroupAndJoin(
                                access, byStage, resolvedFilter, daoMethod, baseFieldExtractor, stageExtractor),
                        (access, resolvedFilter, statusCounts) -> ReactivePaginationUtil.toPage(statusCounts, pageable))
                .switchIfEmpty(ReactivePaginationUtil.toPage(List.of(), pageable));
    }

    private Mono<List<StatusEntityCount>> getTicketCountByGroupAndJoin(
            ProcessorAccess access,
            boolean byStage,
            TicketBucketFilter filter,
            BiFunction<ProcessorAccess, TicketBucketFilter, Flux<PerValueCount>> daoMethod,
            Function<TicketBucketFilter, List<IdAndValue<ULong, String>>> baseFieldExtractor,
            Function<TicketBucketFilter, List<IdAndValue<ULong, String>>> stageExtractor) {

        return FlatMapUtil.flatMapMono(
                        () -> byStage ? this.resolveStages(access, filter) : this.resolveStatus(access, filter),
                        sFilter -> daoMethod.apply(access, sFilter).collectList(),
                        (sFilter, perStageCount) -> ReportUtil.toStatusCountsGroupedIds(
                                        perStageCount,
                                        baseFieldExtractor.apply(sFilter),
                                        stageExtractor.apply(sFilter),
                                        sFilter.isIncludeZero(),
                                        sFilter.isIncludePercentage(),
                                        sFilter.isIncludeTotal(),
                                        sFilter.isIncludeNone())
                                .collectList())
                .switchIfEmpty(Mono.just(List.of()));
    }

    private Mono<TicketBucketFilter> resolveProducts(ProcessorAccess access, TicketBucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> this.productService.getAllProducts(
                        access,
                        filter.getProductIds() == null || filter.getProductIds().isEmpty()
                                ? null
                                : filter.getProductIds()),
                products -> Mono.just(filter.filterProductIds(
                                products.stream().map(BaseUpdatableDto::getId).toList())
                        .setProducts(products.stream()
                                .map(product -> IdAndValue.of(product.getId(), product.getName()))
                                .toList())));
    }

    private Mono<TicketBucketFilter> resolveStages(ProcessorAccess access, TicketBucketFilter filter) {

        if (filter.isIncludeNone()) return Mono.just(filter.setStageIds(List.of()));

        if (!filter.isIncludeAll() && filter.getStageIds() == null) return Mono.just(filter);

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.getValuesFlat(
                        access.getAppCode(),
                        access.getClientCode(),
                        null,
                        null,
                        Boolean.TRUE,
                        filter.getStageIds() != null ? filter.getStageIds().toArray(ULong[]::new) : null),
                stages -> Mono.just(filter.filterStageIds(
                                stages.stream().map(BaseUpdatableDto::getId).toList())
                        .setStages(stages.stream()
                                .map(stage -> IdAndValue.of(stage.getId(), stage.getName()))
                                .toList())));
    }

    private Mono<TicketBucketFilter> resolveStatus(ProcessorAccess access, TicketBucketFilter filter) {

        if (filter.isIncludeNone()) return Mono.just(filter.setStageIds(List.of()));

        if (!filter.isIncludeAll() && filter.getStatusIds() == null) return Mono.just(filter);

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.getValuesFlat(
                        access.getAppCode(),
                        access.getClientCode(),
                        null,
                        null,
                        Boolean.FALSE,
                        filter.getStatusIds() != null ? filter.getStatusIds().toArray(ULong[]::new) : null),
                statuses -> Mono.just(filter.filterStatusIds(
                                statuses.stream().map(BaseUpdatableDto::getId).toList())
                        .setStages(statuses.stream()
                                .map(status -> IdAndValue.of(status.getId(), status.getName()))
                                .toList())));
    }
}
