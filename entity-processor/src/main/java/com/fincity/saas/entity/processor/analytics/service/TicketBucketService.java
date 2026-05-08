package com.fincity.saas.entity.processor.analytics.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.dao.TicketBucketDAO;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.DateStatusCount;
import com.fincity.saas.entity.processor.analytics.model.EntityDateCount;
import com.fincity.saas.entity.processor.analytics.model.EntityEntityCount;
import com.fincity.saas.entity.processor.analytics.model.FilterableListResponse;
import com.fincity.saas.entity.processor.analytics.model.FilterablePageResponse;
import com.fincity.saas.entity.processor.analytics.model.StageHierarchy;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.model.common.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.common.PerValueCount;
import com.fincity.saas.entity.processor.analytics.service.base.BaseAnalyticsService;
import com.fincity.saas.entity.processor.analytics.util.ReactivePaginationUtil;
import com.fincity.saas.entity.processor.analytics.util.ReportUtil;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketBucketService extends BaseAnalyticsService<EntityProcessorTicketsRecord, Ticket, TicketBucketDAO> {

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET;
    }

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerAssignedUserStageCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> super.resolveAssignedUsers(access, f),
                        filter.isOnlyCurrentStageStatus()
                                ? this.dao::getTicketPerAssignedUserCurrentStageCount
                                : this.dao::getTicketPerAssignedUserStageCount,
                        sFilter -> sFilter.getBaseFieldData().getAssignedUsers(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStageCount"));
    }

    public Mono<List<StatusEntityCount>> getTicketPerCreatedByStageCount(
            ProcessorAccess access, TicketBucketFilter filter) {
        return this.resolveProductTemplates(access, filter)
                .flatMap(ptFilter -> this.getTicketCountByGroupAndJoin(
                        access,
                        Boolean.TRUE,
                        ptFilter,
                        filter.isOnlyCurrentStageStatus()
                                ? this.dao::getTicketPerCreatedByCurrentStageCount
                                : this.dao::getTicketPerCreatedByStageCount,
                        sFilter -> sFilter.getBaseFieldData().getCreatedBys(),
                        sFilter -> sFilter.getFieldData().getStages()))
                .switchIfEmpty(Mono.just(List.of()))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "TicketBucketService.getTicketPerCreatedByStageCount[ProcessorAccess, TicketBucketFilter]"));
    }

    public Mono<FilterableListResponse<DateStatusCount>> getTicketPerAssignedUserStageSourceDateCount(
            TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProductTemplates(access, filter),
                        (access, ptFilter) -> resolveStages(access, ptFilter),
                        (access, ptFilter, sFilter) -> (filter.isOnlyCurrentStageStatus()
                                        ? this.dao.getTicketPerAssignedUserCurrentStageSourceDateCount(access, sFilter)
                                        : this.dao.getTicketPerAssignedUserStageSourceDateCount(access, sFilter))
                                .collectList(),
                        (access, ptFilter, sFilter, perStageCount) -> ReportUtil.toDateStatusCounts(
                                        perStageCount, sFilter.getFieldData().getStages(), sFilter.toReportOptions())
                                .collectList(),
                        (access, ptFilter, sFilter, perStageCount, dateStatusCounts) ->
                                toFilterableListResponse(access, dateStatusCounts, sFilter))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerAssignedUserStageSourceDateCount"));
    }

    public Mono<FilterableListResponse<DateStatusCount>> getTicketPerCreatedByStageTotalWithUniqueCreatedByWithClientId(
            TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProductTemplates(access, filter),
                        (access, ptFilter) -> resolveStages(access, ptFilter),
                        (access, ptFilter, sFilter) -> Mono.zip(
                                (filter.isOnlyCurrentStageStatus()
                                                ? this.dao.getTicketCountPerCurrentStageAndDateWithClientId(
                                                        access, sFilter, TimePeriod.DAYS)
                                                : this.dao.getTicketCountPerStageAndDateWithClientId(
                                                        access, sFilter, TimePeriod.DAYS))
                                        .collectList(),
                                (filter.isOnlyCurrentStageStatus()
                                                ? this.dao.getUniqueCreatedByCountPerCurrentStageAndDateWithClientId(
                                                        access, sFilter, sFilter.getTimePeriod())
                                                : this.dao.getUniqueCreatedByCountPerStageAndDateWithClientId(
                                                        access, sFilter, sFilter.getTimePeriod()))
                                        .collectList()),
                        (access, ptFilter, sFilter, countsTuple) -> {
                            List<PerDateCount> perStageCount = countsTuple.getT1();
                            List<PerDateCount> uniqueCreatedByPerStage = countsTuple.getT2();

                            List<PerDateCount> mergedList = new ArrayList<>(perStageCount);

                            for (PerDateCount pdc : uniqueCreatedByPerStage) {
                                mergedList.add(new PerDateCount()
                                        .setGroupedValue(null)
                                        .setMapValue("#" + pdc.getMapValue())
                                        .setDate(pdc.getDate())
                                        .setCount(pdc.getCount()));
                            }

                            return ReportUtil.toDateStatusCountsAggregatedTotal(
                                            mergedList, sFilter.getFieldData().getStages(), sFilter.toReportOptions())
                                    .collectList();
                        },
                        (access, ptFilter, sFilter, countsTuple, dateStatusCounts) ->
                                toFilterableListResponse(access, dateStatusCounts, sFilter))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "TicketBucketService.getTicketPerCreatedByStageTotalWithUniqueCreatedByWithClientId"));
    }

    public Mono<FilterablePageResponse<EntityEntityCount>> getTicketPerProductStageAndClientIdCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProductTemplates(access, filter),
                        (access, ptFilter) -> resolveProducts(access, ptFilter),
                        (access, ptFilter, productFilter) -> this.resolveStages(access, productFilter),
                        (access, ptFilter, productFilter, stageFilter) ->
                                super.resolveClients(access, stageFilter),
                        (access, ptFilter, productFilter, stageFilter, clientFilter) ->
                                (filter.isOnlyCurrentStageStatus()
                                                ? this.dao.getTicketCountPerProductCurrentStageAndClientId(
                                                        access, stageFilter)
                                                : this.dao.getTicketCountPerProductStageAndClientId(
                                                        access, stageFilter))
                                        .collectList(),
                        (access, ptFilter, productFilter, stageFilter, clientFilter, perValueCountList) ->
                                ReportUtil.toEntityStageCounts(
                                                perValueCountList,
                                                clientFilter.getFieldData().getProducts(),
                                                clientFilter.getBaseFieldData().getClients(),
                                                clientFilter.isIncludeZero(),
                                                clientFilter.isIncludePercentage(),
                                                clientFilter.isIncludeAllTotal(),
                                                clientFilter.getBaseFieldData().getClientManagersByClientId())
                                        .collectList(),
                        (access, ptFilter, productFilter, stageFilter, clientFilter, perValueCountList,
                                entityStatusCounts) ->
                                toFilterablePageResponse(access, entityStatusCounts, clientFilter, pageable))
                .switchIfEmpty(toFilterablePageResponse(List.of(), pageable))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProductStageAndClientIdCount"));
    }

    public Mono<FilterablePageResponse<EntityDateCount>> getTicketPerClientIdAndDateCount(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProductTemplates(access, filter),
                        (access, ptFilter) -> super.resolveClients(access, ptFilter),
                        (access, ptFilter, clientFilter) -> (filter.isOnlyCurrentStageStatus()
                                        ? this.dao.getTicketCountPerClientIdAndDateCurrentStage(access, clientFilter)
                                        : this.dao.getTicketCountPerClientIdAndDate(access, clientFilter))
                                .collectList(),
                        (access, ptFilter, clientFilter, perDateCountList) -> ReportUtil.toEntityDateCounts(
                                        perDateCountList,
                                        clientFilter.getBaseFieldData().getClients(),
                                        clientFilter.toReportOptions(),
                                        clientFilter.getBaseFieldData().getClientManagersByClientId())
                                .collectList(),
                        (access, ptFilter, clientFilter, perDateCountList, entityDateCounts) ->
                                toFilterablePageResponse(access, entityDateCounts, clientFilter, pageable))
                .switchIfEmpty(toFilterablePageResponse(List.of(), pageable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerClientIdAndDateCount"));
    }

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerAssignedUserStatusCount(
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

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerCreatedByStageCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> super.resolveCreatedBys(access, f),
                        filter.isOnlyCurrentStageStatus()
                                ? this.dao::getTicketPerCreatedByCurrentStageCount
                                : this.dao::getTicketPerCreatedByStageCount,
                        sFilter -> sFilter.getBaseFieldData().getCreatedBys(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerCreatedByStageCount"));
    }

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerCreatedByStatusCount(
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

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerClientIdStageCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> super.resolveClients(access, f),
                        filter.isOnlyCurrentStageStatus()
                                ? this.dao::getTicketPerClientIdCurrentStageCount
                                : this.dao::getTicketPerClientIdStageCount,
                        sFilter -> sFilter.getBaseFieldData().getClients(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerClientIdStageCount"));
    }

    public Mono<List<StatusEntityCount>> getTicketPerClientIdStageCount(
            ProcessorAccess access, TicketBucketFilter filter) {
        return this.resolveProductTemplates(access, filter)
                .flatMap(ptFilter -> this.getTicketCountByGroupAndJoin(
                        access,
                        Boolean.TRUE,
                        ptFilter,
                        filter.isOnlyCurrentStageStatus()
                                ? this.dao::getTicketPerClientIdCurrentStageCount
                                : this.dao::getTicketPerClientIdStageCount,
                        sFilter -> sFilter.getBaseFieldData().getClients(),
                        sFilter -> sFilter.getFieldData().getStages()))
                .switchIfEmpty(Mono.just(List.of()))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "TicketBucketService.getTicketPerClientIdStageCount[ProcessorAccess, TicketBucketFilter]"));
    }

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerClientIdStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
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

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerProjectStageCount(
            Pageable pageable, TicketBucketFilter filter) {
        return this.getTicketCountByGroupAndJoin(
                        pageable,
                        Boolean.TRUE,
                        filter,
                        access -> f -> this.resolveProducts(access, f),
                        filter.isOnlyCurrentStageStatus()
                                ? this.dao::getTicketPerProjectCurrentStageCount
                                : this.dao::getTicketPerProjectStageCount,
                        sFilter -> sFilter.getFieldData().getProducts(),
                        sFilter -> sFilter.getFieldData().getStages())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProjectStageCount"));
    }

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerProjectStatusCount(
            Pageable pageable, TicketBucketFilter filter) {
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

    public Mono<FilterablePageResponse<StatusEntityCount>> getTicketPerProjectStageCountForLoggedInClient(
            Pageable pageable, TicketBucketFilter filter) {
        return FlatMapUtil.flatMapMono(super::hasAccess, access -> {
                    if (!access.isHasBpAccess())
                        return super.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                ProcessorMessageResourceService.PARTNER_ACCESS_DENIED);

                    if (access.getUserInherit() == null
                            || access.getUserInherit().getLoggedInClientId() == null)
                        return super.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.MISSING_PARAMETERS,
                                "Logged in client information not available");

                    return this.getTicketCountByGroupAndJoin(
                            pageable,
                            Boolean.TRUE,
                            filter,
                            acc -> f -> this.resolveProducts(acc, f),
                            filter.isOnlyCurrentStageStatus()
                                    ? this.dao::getTicketPerProjectCurrentStageCount
                                    : this.dao::getTicketPerProjectStageCount,
                            sFilter -> sFilter.getFieldData().getProducts(),
                            sFilter -> sFilter.getFieldData().getStages());
                })
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "TicketBucketService.getTicketPerProjectStageCountForLoggedInClient"));
    }

    private Mono<FilterablePageResponse<StatusEntityCount>> getTicketCountByGroupAndJoin(
            Pageable pageable,
            boolean byStage,
            TicketBucketFilter filter,
            Function<ProcessorAccess, Function<TicketBucketFilter, Mono<TicketBucketFilter>>> resolver,
            BiFunction<ProcessorAccess, TicketBucketFilter, Flux<PerValueCount>> daoMethod,
            Function<TicketBucketFilter, List<IdAndValue<ULong, String>>> baseFieldExtractor,
            Function<TicketBucketFilter, List<IdAndValue<ULong, String>>> stageExtractor) {

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.resolveProductTemplates(access, filter),
                        (access, ptFilter) -> resolver.apply(access).apply(ptFilter),
                        (access, ptFilter, resolvedFilter) -> this.getTicketCountByGroupAndJoin(
                                access, byStage, resolvedFilter, daoMethod, baseFieldExtractor, stageExtractor),
                        (access, ptFilter, resolvedFilter, statusCounts) ->
                                toFilterablePageResponse(access, statusCounts, resolvedFilter, pageable))
                .switchIfEmpty(toFilterablePageResponse(List.of(), pageable));
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

    private <T> Mono<FilterablePageResponse<T>> toFilterablePageResponse(
            ProcessorAccess access, List<T> results, TicketBucketFilter filter, Pageable pageable) {

        return Mono.zip(
                        this.dao.getDistinctClientIdsForDateRange(access, filter).collectList(),
                        this.dao.getDistinctProductIdsForDateRange(access, filter).collectList(),
                        this.dao.getDistinctAssignedUserIdsForDateRange(access, filter).collectList(),
                        buildStageHierarchies(access, filter))
                .flatMap(distinctIds -> {
                    List<ULong> clientIds = distinctIds.getT1();
                    List<ULong> productIds = distinctIds.getT2();
                    List<ULong> assignedUserIds = distinctIds.getT3();
                    List<StageHierarchy> stageHierarchies = distinctIds.getT4();

                    return Mono.zip(
                                    resolveClients(clientIds),
                                    resolveProducts(access, productIds),
                                    resolveAssignedUsers(assignedUserIds),
                                    resolveSelectedProductTemplates(access, filter))
                            .flatMap(resolved -> ReactivePaginationUtil.toPage(results, pageable)
                                    .map(page -> FilterablePageResponse.of(page,
                                            resolved.getT1(), resolved.getT2(),
                                            resolved.getT3(), extractClientManagers(resolved.getT1()),
                                            resolved.getT4().getT1(), resolved.getT4().getT2(),
                                            stageHierarchies)));
                });
    }

    private static <T> Mono<FilterablePageResponse<T>> toFilterablePageResponse(List<T> results, Pageable pageable) {
        return ReactivePaginationUtil.toPage(results, pageable)
                .map(page -> FilterablePageResponse.of(page, List.of(), List.of(), List.of(), List.of()));
    }

    private <T> Mono<FilterableListResponse<T>> toFilterableListResponse(
            ProcessorAccess access, List<T> results, TicketBucketFilter filter) {

        return Mono.zip(
                        this.dao.getDistinctClientIdsForDateRange(access, filter).collectList(),
                        this.dao.getDistinctProductIdsForDateRange(access, filter).collectList(),
                        this.dao.getDistinctAssignedUserIdsForDateRange(access, filter).collectList(),
                        buildStageHierarchies(access, filter))
                .flatMap(distinctIds -> Mono.zip(
                                resolveClients(distinctIds.getT1()),
                                resolveProducts(access, distinctIds.getT2()),
                                resolveAssignedUsers(distinctIds.getT3()),
                                resolveSelectedProductTemplates(access, filter))
                        .map(resolved -> FilterableListResponse.of(results,
                                resolved.getT1(), resolved.getT2(),
                                resolved.getT3(), extractClientManagers(resolved.getT1()),
                                resolved.getT4().getT1(), resolved.getT4().getT2(),
                                distinctIds.getT4())));
    }

    private Mono<List<StageHierarchy>> buildStageHierarchies(ProcessorAccess access, TicketBucketFilter filter) {

        List<ULong> selectedIds = filter.getFieldData().getSelectedProductTemplateIds();
        if (selectedIds == null || selectedIds.isEmpty())
            return Mono.just(List.of());

        List<ULong> nonNullIds = selectedIds.stream().filter(Objects::nonNull).toList();
        if (nonNullIds.isEmpty()) return Mono.just(List.of());

        return Flux.fromIterable(nonNullIds)
                .flatMap(templateId -> this.stageService
                        .getValuesFlat(access.getAppCode(), access.getClientCode(), null, templateId, null)
                        .map(StageHierarchy::from)
                        .defaultIfEmpty(StageHierarchy.from(List.of())))
                .collectList();
    }

    private Mono<reactor.util.function.Tuple2<List<ProductTemplate>, List<ProductTemplate>>>
            resolveSelectedProductTemplates(ProcessorAccess access, TicketBucketFilter filter) {

        List<ULong> selectedIds = filter.getFieldData().getSelectedProductTemplateIds();
        List<IdAndValue<ULong, String>> templateIdAndValues = filter.getFieldData().getProductTemplates();

        if (templateIdAndValues == null || templateIdAndValues.isEmpty())
            return Mono.just(reactor.util.function.Tuples.of(List.of(), List.of()));

        return Flux.fromIterable(templateIdAndValues)
                .filter(Objects::nonNull)
                .map(IdAndValue::getId)
                .filter(Objects::nonNull)
                .flatMap(id -> this.productTemplateService.readById(access, id))
                .collectList()
                .map(allTemplates -> {
                    List<ProductTemplate> selected = (selectedIds == null || selectedIds.isEmpty())
                            ? allTemplates
                            : allTemplates.stream()
                                    .filter(pt -> selectedIds.contains(pt.getId()))
                                    .toList();
                    return reactor.util.function.Tuples.of(allTemplates, selected);
                });
    }

    private Mono<List<Client>> resolveClients(List<ULong> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) return Mono.just(List.of());

        List<BigInteger> idsBigInt = clientIds.stream().map(ULong::toBigInteger).toList();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("fetchClientManagers", "true");

        return this.securityService
                .getClientInternalBatch(idsBigInt, params)
                .defaultIfEmpty(List.of());
    }

    private Mono<List<User>> resolveAssignedUsers(List<ULong> userIds) {
        if (userIds == null || userIds.isEmpty()) return Mono.just(List.of());

        List<BigInteger> idsBigInt = userIds.stream().map(ULong::toBigInteger).toList();
        return this.securityService
                .getUsersInternalBatch(idsBigInt, null)
                .defaultIfEmpty(List.of());
    }

    private static List<User> extractClientManagers(List<Client> clients) {
        if (clients == null || clients.isEmpty()) return List.of();

        return clients.stream()
                .filter(c -> c.getClientManagers() != null)
                .flatMap(c -> c.getClientManagers().stream())
                .filter(u -> u != null && u.getId() != null)
                .distinct()
                .toList();
    }

    private Mono<List<Product>> resolveProducts(ProcessorAccess access, List<ULong> productIds) {
        if (productIds == null || productIds.isEmpty()) return Mono.just(List.of());

        return this.productService
                .getAllProducts(access, productIds)
                .defaultIfEmpty(List.of());
    }

    private Mono<TicketBucketFilter> resolveProductTemplates(ProcessorAccess access, TicketBucketFilter filter) {

        if (filter.getProductIds() != null && !filter.getProductIds().isEmpty()) {
            return resolveProductTemplateFromProducts(access, filter);
        }

        return this.dao
                .getDistinctProductTemplateIds(access, filter)
                .collectList()
                .flatMap(distinctTemplateIds -> {
                    if (distinctTemplateIds.isEmpty()) return Mono.just(filter);

                    List<ULong> effectiveIds = (filter.getProductTemplateIds() != null
                                    && !filter.getProductTemplateIds().isEmpty())
                            ? com.fincity.saas.entity.processor.util.CollectionUtil.intersectLists(
                                    filter.getProductTemplateIds(), distinctTemplateIds)
                            : distinctTemplateIds;

                    if (effectiveIds.isEmpty()) return Mono.just(filter);

                    return Flux.fromIterable(effectiveIds)
                            .flatMap(id -> this.productTemplateService.readById(access, id))
                            .collectList()
                            .flatMap(templates -> applyProductTemplateToFilter(access, filter, templates));
                });
    }

    private Mono<TicketBucketFilter> resolveProductTemplateFromProducts(
            ProcessorAccess access, TicketBucketFilter filter) {

        return this.productService
                .getAllProducts(access, filter.getProductIds())
                .defaultIfEmpty(List.of())
                .flatMap(products -> {
                    if (products.isEmpty()) return Mono.just(filter);

                    filter.filterProductIds(
                                    products.stream().map(BaseUpdatableDto::getId).toList())
                            .setProducts(products.stream()
                                    .map(p -> IdAndValue.of(p.getId(), p.getName()))
                                    .toList());

                    List<ULong> distinctTemplateIds = products.stream()
                            .map(Product::getProductTemplateId)
                            .filter(id -> id != null)
                            .distinct()
                            .toList();

                    if (distinctTemplateIds.isEmpty()) return Mono.just(filter);

                    return Flux.fromIterable(distinctTemplateIds)
                            .flatMap(id -> this.productTemplateService.readById(access, id))
                            .collectList()
                            .map(templates -> {
                                List<ULong> templateIds = templates.stream()
                                        .map(BaseUpdatableDto::getId)
                                        .filter(Objects::nonNull)
                                        .toList();
                                filter.setSelectedProductTemplateIds(templateIds)
                                        .setProductTemplates(templates.stream()
                                                .filter(pt -> pt.getId() != null)
                                                .map(pt -> IdAndValue.of(pt.getId(), pt.getName()))
                                                .toList())
                                        .filterProductTemplateIds(distinctTemplateIds);
                                return filter;
                            });
                });
    }

    private Mono<TicketBucketFilter> applyProductTemplateToFilter(
            ProcessorAccess access, TicketBucketFilter filter, List<ProductTemplate> templates) {

        if (templates == null || templates.isEmpty()) return Mono.just(filter);

        templates.sort(Comparator.comparing(
                (ProductTemplate pt) -> pt.getCreatedAt(),
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<ULong> allTemplateIds = templates.stream()
                .map(BaseUpdatableDto::getId)
                .filter(Objects::nonNull)
                .toList();

        filter.filterProductTemplateIds(allTemplateIds)
                .setProductTemplates(templates.stream()
                        .filter(pt -> pt.getId() != null)
                        .map(pt -> IdAndValue.of(pt.getId(), pt.getName()))
                        .toList())
                .setSelectedProductTemplateIds(allTemplateIds);

        return this.productService
                .getAllProducts(access, null)
                .defaultIfEmpty(List.of())
                .map(allProducts -> {
                    List<Product> templateProducts = allProducts.stream()
                            .filter(p -> allTemplateIds.contains(p.getProductTemplateId()))
                            .toList();

                    filter.filterProductIds(
                                    templateProducts.stream().map(BaseUpdatableDto::getId).toList())
                            .setProducts(templateProducts.stream()
                                    .map(product -> IdAndValue.of(product.getId(), product.getName()))
                                    .toList());

                    return filter;
                });
    }

    private Mono<TicketBucketFilter> resolveProducts(ProcessorAccess access, TicketBucketFilter filter) {

        return this.productService
                .getAllProducts(
                        access,
                        filter.getProductIds() == null || filter.getProductIds().isEmpty()
                                ? null
                                : filter.getProductIds())
                .defaultIfEmpty(List.of())
                .map(products -> filter.filterProductIds(
                                products.stream().map(BaseUpdatableDto::getId).toList())
                        .setProducts(products.stream()
                                .map(product -> IdAndValue.of(product.getId(), product.getName()))
                                .toList()));
    }

    private Mono<TicketBucketFilter> resolveStages(ProcessorAccess access, TicketBucketFilter filter) {

        if (filter.isIncludeNone()) return Mono.just(filter.setStageIds(List.of()));

        if (!filter.isIncludeAll() && filter.getStageIds() == null) return Mono.just(filter);

        List<ULong> selectedIds = filter.getFieldData().getSelectedProductTemplateIds();
        ULong productTemplateId = (selectedIds != null && !selectedIds.isEmpty()) ? selectedIds.get(0) : null;

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.getValuesFlat(
                        access.getAppCode(),
                        access.getClientCode(),
                        null,
                        productTemplateId,
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

        List<ULong> selectedIds = filter.getFieldData().getSelectedProductTemplateIds();
        ULong productTemplateId = (selectedIds != null && !selectedIds.isEmpty()) ? selectedIds.get(0) : null;

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.getValuesFlat(
                        access.getAppCode(),
                        access.getClientCode(),
                        null,
                        productTemplateId,
                        Boolean.FALSE,
                        filter.getStatusIds() != null ? filter.getStatusIds().toArray(ULong[]::new) : null),
                statuses -> Mono.just(filter.filterStatusIds(
                                statuses.stream().map(BaseUpdatableDto::getId).toList())
                        .setStatuses(statuses.stream()
                                .map(status -> IdAndValue.of(status.getId(), status.getName()))
                                .toList())));
    }
}
