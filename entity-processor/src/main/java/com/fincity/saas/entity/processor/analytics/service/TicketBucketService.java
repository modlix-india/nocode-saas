package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.analytics.model.StatusCount;
import com.fincity.saas.entity.processor.analytics.service.base.BaseAnalyticsService;
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
import reactor.core.publisher.Mono;

@Service
public class TicketBucketService extends BaseAnalyticsService<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO> {

    public Mono<Page<StatusCount>> getTicketPerAssignedUserStatusCount(Pageable pageable, BucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.resolveAssignedUsers(access, filter),
                this::resolveStages,
                (access, aFilter, sFilter) -> super.updateClientIds(access, sFilter),
                (access, aFilter, sFilter, cFilter) -> super.updateCreatedByIds(access, cFilter),
                (access, aFilter, sFilter, cFilter, cbFilter) -> this.dao
                        .getTicketPerAssignedUserStageCount(access, cbFilter)
                        .collectList(),
                (access, aFilter, sFilter, cFilter, cbFilter, perStageCount) -> ReportUtil.toStatusCounts(
                                perStageCount,
                                cbFilter.getFieldData().getAssignedUsers(),
                                cbFilter.getFieldData().getStages(),
                                cbFilter.isIncludeZero())
                        .collectList(),
                (access, aFilter, sFilter, cFilter, cbFilter, perStageCount, perStatusCount) ->
                        ReactivePaginationUtil.toPage(perStatusCount, pageable));
    }

    public Mono<Page<StatusCount>> getTicketPerCreatedByStatusCount(Pageable pageable, BucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.resolveCreatedBys(access, filter),
                this::resolveStages,
                (access, cbFilter, sFilter) -> super.updateClientIds(access, sFilter),
                (access, cbFilter, sFilter, cFilter) -> super.updateAssignedUserIds(access, cFilter),
                (access, cbFilter, sFilter, cFilter, aFilter) -> this.dao
                        .getTicketPerCreatedByStageCount(access, aFilter)
                        .collectList(),
                (access, cbFilter, sFilter, cFilter, aFilter, perStageCount) -> ReportUtil.toStatusCounts(
                                perStageCount,
                                aFilter.getFieldData().getCreatedBys(),
                                aFilter.getFieldData().getStages(),
                                aFilter.isIncludeZero())
                        .collectList(),
                (access, cbFilter, sFilter, cFilter, aFilter, perStageCount, perStatusCount) ->
                        ReactivePaginationUtil.toPage(perStatusCount, pageable));
    }

    public Mono<Page<StatusCount>> getTicketPerClientIdStatusCount(Pageable pageable, BucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> super.resolveClients(access, filter),
                this::resolveStages,
                (access, cFilter, sFilter) -> super.updateCreatedByIds(access, sFilter),
                (access, cFilter, sFilter, cbFilter) -> super.updateAssignedUserIds(access, cbFilter),
                (access, cFilter, sFilter, cbFilter, aFilter) ->
                        this.dao.getTicketPerClientIdStageCount(access, aFilter).collectList(),
                (access, cFilter, sFilter, cbFilter, aFilter, perStageCount) -> ReportUtil.toStatusCounts(
                                perStageCount,
                                aFilter.getFieldData().getClients(),
                                aFilter.getFieldData().getStages(),
                                aFilter.isIncludeZero())
                        .collectList(),
                (access, cFilter, sFilter, cbFilter, aFilter, perStageCount, perStatusCount) ->
                        ReactivePaginationUtil.toPage(perStatusCount, pageable));
    }

    public Mono<Page<StatusCount>> getTicketPerProductIdStatusCount(Pageable pageable, BucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.resolveProducts(access, filter),
                this::resolveStages,
                (access, pFilter, sFilter) -> super.updateClientIds(access, sFilter),
                (access, pFilter, sFilter, cFilter) -> super.updateCreatedByIds(access, cFilter),
                (access, pFilter, sFilter, cFilter, cbFilter) -> super.updateAssignedUserIds(access, cbFilter),
                (access, pFilter, sFilter, cFilter, cbFilter, aFilter) ->
                        this.dao.getTicketPerProjectStageCount(access, aFilter).collectList(),
                (access, pFilter, sFilter, cFilter, cbFilter, aFilter, perStageCount) -> ReportUtil.toStatusCounts(
                                perStageCount,
                                aFilter.getFieldData().getProducts(),
                                aFilter.getFieldData().getStages(),
                                aFilter.isIncludeZero())
                        .collectList(),
                (access, pFilter, sFilter, cFilter, cbFilter, aFilter, perStageCount, perStatusCount) ->
                        ReactivePaginationUtil.toPage(perStatusCount, pageable));
    }

    public Mono<BucketFilter> resolveProducts(ProcessorAccess access, BucketFilter filter) {

        return FlatMapUtil.flatMapMono(
                () -> this.productService.getAllProducts(access, filter.getProductIds()),
                products -> Mono.just(filter.filterProductIds(
                                products.stream().map(BaseUpdatableDto::getId).toList())
                        .setProducts(products.stream()
                                .map(product -> IdAndValue.of(product.getId(), product.getName()))
                                .toList())));
    }

    public Mono<BucketFilter> resolveStages(ProcessorAccess access, BucketFilter filter) {

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
