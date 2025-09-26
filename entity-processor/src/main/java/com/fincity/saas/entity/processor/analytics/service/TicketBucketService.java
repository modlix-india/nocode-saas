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
                        (access, aFilter, sFilter) -> super.updateClientIds(access, sFilter),
                        (access, aFilter, sFilter, cFilter) -> super.updateCreatedByIds(access, cFilter),
                        (access, aFilter, sFilter, cFilter, cbFilter) -> this.dao
                                .getTicketPerAssignedUserStageCount(access, cbFilter)
                                .collectList(),
                        (access, aFilter, sFilter, cFilter, cbFilter, perStageCount) ->
                                ReportUtil.toStatusCountsGroupedIds(
                                                perStageCount,
                                                cbFilter.getBaseFieldData().getAssignedUsers(),
                                                cbFilter.getFieldData().getStages(),
                                                cbFilter.isIncludeZero(),
                                                cbFilter.isIncludePercentage(),
                                                cbFilter.isIncludeTotal())
                                        .collectList(),
                        (access, aFilter, sFilter, cFilter, cbFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStatusCount"));
    }

    public Flux<DateStatusCount> getTicketPerAssignedUserStageSourceDateCount(TicketBucketFilter filter) {
        return FlatMapUtil.flatMapFlux(
                        () -> super.hasAccess().flux(),
                        access -> super.updateAssignedUserIds(access, filter).flux(),
                        (access1, filter1) -> resolveStages(access1, filter1).flux(),
                        (access, aFilter, sFilter) ->
                                super.updateClientIds(access, sFilter).flux(),
                        (access, aFilter, sFilter, cFilter) ->
                                super.updateCreatedByIds(access, cFilter).flux(),
                        (access, aFilter, sFilter, cFilter, cbFilter) -> this.dao
                                .getTicketPerAssignedUserStageSourceDateCount(access, cbFilter)
                                .collectList()
                                .flux(),
                        (access, aFilter, sFilter, cFilter, cbFilter, perStageCount) -> ReportUtil.toDateStatusCounts(
                                DatePair.of(filter.getStartDate(), filter.getEndDate()),
                                filter.getTimePeriod(),
                                perStageCount,
                                cbFilter.getFieldData().getStages(),
                                cbFilter.isIncludeZero(),
                                cbFilter.isIncludePercentage(),
                                cbFilter.isIncludeTotal()))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerCreatedByStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.resolveCreatedBys(access, filter),
                        this::resolveStages,
                        (access, cbFilter, sFilter) -> super.updateClientIds(access, sFilter),
                        (access, cbFilter, sFilter, cFilter) -> super.updateAssignedUserIds(access, cFilter),
                        (access, cbFilter, sFilter, cFilter, aFilter) -> this.dao
                                .getTicketPerCreatedByStageCount(access, aFilter)
                                .collectList(),
                        (access, cbFilter, sFilter, cFilter, aFilter, perStageCount) ->
                                ReportUtil.toStatusCountsGroupedIds(
                                                perStageCount,
                                                aFilter.getBaseFieldData().getCreatedBys(),
                                                aFilter.getFieldData().getStages(),
                                                cbFilter.isIncludeZero(),
                                                cbFilter.isIncludePercentage(),
                                                cbFilter.isIncludeTotal())
                                        .collectList(),
                        (access, cbFilter, sFilter, cFilter, aFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerCreatedByStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerClientIdStatusCount(Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.resolveClients(access, filter),
                        this::resolveStages,
                        (access, cFilter, sFilter) -> super.updateCreatedByIds(access, sFilter),
                        (access, cFilter, sFilter, cbFilter) -> super.updateAssignedUserIds(access, cbFilter),
                        (access, cFilter, sFilter, cbFilter, aFilter) -> this.dao
                                .getTicketPerClientIdStageCount(access, aFilter)
                                .collectList(),
                        (access, cFilter, sFilter, cbFilter, aFilter, perStageCount) ->
                                ReportUtil.toStatusCountsGroupedIds(
                                                perStageCount,
                                                aFilter.getBaseFieldData().getClients(),
                                                aFilter.getFieldData().getStages(),
                                                cbFilter.isIncludeZero(),
                                                cbFilter.isIncludePercentage(),
                                                cbFilter.isIncludeTotal())
                                        .collectList(),
                        (access, cFilter, sFilter, cbFilter, aFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerClientIdStatusCount"));
    }

    public Mono<Page<StatusEntityCount>> getTicketPerProductIdStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProducts(access, filter),
                        this::resolveStages,
                        (access, pFilter, sFilter) -> super.updateClientIds(access, sFilter),
                        (access, pFilter, sFilter, cFilter) -> super.updateCreatedByIds(access, cFilter),
                        (access, pFilter, sFilter, cFilter, cbFilter) -> super.updateAssignedUserIds(access, cbFilter),
                        (access, pFilter, sFilter, cFilter, cbFilter, aFilter) -> this.dao
                                .getTicketPerProjectStageCount(access, aFilter)
                                .collectList(),
                        (access, pFilter, sFilter, cFilter, cbFilter, aFilter, perStageCount) ->
                                ReportUtil.toStatusCountsGroupedIds(
                                                perStageCount,
                                                aFilter.getFieldData().getProducts(),
                                                aFilter.getFieldData().getStages(),
                                                cbFilter.isIncludeZero(),
                                                cbFilter.isIncludePercentage(),
                                                cbFilter.isIncludeTotal())
                                        .collectList(),
                        (access, pFilter, sFilter, cFilter, cbFilter, aFilter, perStageCount, perStatusCount) ->
                                ReactivePaginationUtil.toPage(perStatusCount, pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProductIdStatusCount"));
    }

    public Mono<TicketBucketFilter> resolveProducts(ProcessorAccess access, TicketBucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> this.productService.getAllProducts(access, filter.getProductIds()),
                products -> Mono.just(filter.filterProductIds(
                                products.stream().map(BaseUpdatableDto::getId).toList())
                        .setProducts(products.stream()
                                .map(product -> IdAndValue.of(product.getId(), product.getName()))
                                .toList())));
    }

    public Mono<TicketBucketFilter> resolveStages(ProcessorAccess access, TicketBucketFilter filter) {

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
