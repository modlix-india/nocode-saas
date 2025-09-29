package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.service.base.BaseAnalyticsService;
import com.fincity.saas.entity.processor.analytics.util.DatePair;
import com.fincity.saas.entity.processor.analytics.util.ReactivePaginationUtil;
import com.fincity.saas.entity.processor.analytics.util.ReportUtil;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketBucketService extends BaseAnalyticsService<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO> {

    public Mono<Page<StatusEntityCount>> getTicketPerAssignedUserStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.resolveAssignedUsers(access, filter),
                        this::resolveStages,
                        (access, aFilter, sFilter) -> this.dao
                                .getTicketPerAssignedUserStageCount(access, sFilter)
                                .collectList(),
                        (access, aFilter, sFilter, perStageCount) -> ReportUtil.toStatusCountsGroupedIds(
                                        perStageCount,
                                        sFilter.getBaseFieldData().getAssignedUsers(),
                                        sFilter.getFieldData().getStages(),
                                        sFilter.isIncludeZero(),
                                        sFilter.isIncludePercentage(),
                                        sFilter.isIncludeTotal(),
                                        sFilter.isIncludeNone())
                                .collectList(),
                        (access, aFilter, sFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .switchIfEmpty(ReactivePaginationUtil.toPage(List.of(), pageable))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStatusCount"));
    }

    public Flux<DateStatusCount> getTicketPerAssignedUserStageSourceDateCount(TicketBucketFilter filter) {
        return FlatMapUtil.flatMapFlux(
                        () -> super.hasAccess().flux(),
                        (access) -> resolveStages(access, filter).flux(),
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
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerCreatedByStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.resolveCreatedBys(access, filter),
                        this::resolveStages,
                        (access, cbFilter, sFilter) -> this.dao
                                .getTicketPerCreatedByStageCount(access, sFilter)
                                .collectList(),
                        (access, cbFilter, sFilter, perStageCount) -> ReportUtil.toStatusCountsGroupedIds(
                                        perStageCount,
                                        sFilter.getBaseFieldData().getCreatedBys(),
                                        sFilter.getFieldData().getStages(),
                                        cbFilter.isIncludeZero(),
                                        cbFilter.isIncludePercentage(),
                                        cbFilter.isIncludeTotal(),
                                        cbFilter.isIncludeNone())
                                .collectList(),
                        (access, cbFilter, sFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .switchIfEmpty(ReactivePaginationUtil.toPage(List.of(), pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerCreatedByStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerClientIdStatusCount(Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.resolveClients(access, filter),
                        this::resolveStages,
                        (access, cFilter, sFilter) -> this.dao
                                .getTicketPerClientIdStageCount(access, sFilter)
                                .collectList(),
                        (access, cFilter, sFilter, perStageCount) -> ReportUtil.toStatusCountsGroupedIds(
                                        perStageCount,
                                        sFilter.getBaseFieldData().getClients(),
                                        sFilter.getFieldData().getStages(),
                                        sFilter.isIncludeZero(),
                                        sFilter.isIncludePercentage(),
                                        sFilter.isIncludeTotal(),
                                        sFilter.isIncludeNone())
                                .collectList(),
                        (access, cFilter, sFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .switchIfEmpty(ReactivePaginationUtil.toPage(List.of(), pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerClientIdStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerProductIdStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProducts(access, filter),
                        this::resolveStages,
                        (access, pFilter, sFilter) -> this.dao
                                .getTicketPerProjectStageCount(access, sFilter)
                                .collectList(),
                        (access, pFilter, sFilter, perStageCount) -> ReportUtil.toStatusCountsGroupedIds(
                                        perStageCount,
                                        sFilter.getFieldData().getProducts(),
                                        sFilter.getFieldData().getStages(),
                                        sFilter.isIncludeZero(),
                                        sFilter.isIncludePercentage(),
                                        sFilter.isIncludeTotal(),
                                        sFilter.isIncludeNone())
                                .collectList(),
                        (access, pFilter, sFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .switchIfEmpty(ReactivePaginationUtil.toPage(List.of(), pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProductIdStatusCount"));
    }

    private Mono<TicketBucketFilter> resolveProducts(ProcessorAccess access, TicketBucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> this.productService.getAllProducts(access, filter.getProductIds()),
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
}
