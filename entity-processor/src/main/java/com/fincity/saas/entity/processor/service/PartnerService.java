package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.analytics.model.StatusEntityCount;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.analytics.service.TicketBucketService;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.dao.PartnerDAO;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.functions.AbstractProcessorFunction;
import com.fincity.saas.entity.processor.functions.IRepositoryProvider;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.ListFunctionRepository;
import com.fincity.saas.entity.processor.util.MapSchemaRepository;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PartnerService extends BaseUpdatableService<EntityProcessorPartnersRecord, Partner, PartnerDAO>
        implements IRepositoryProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartnerService.class);
    private static final String PARTNER_CACHE = "Partner";

    private static final String FETCH_PARTNERS = "fetchPartners";

    private static final String FETCH_LEADS = "fetchLeads";

    private final List<ReactiveFunction> functions = new ArrayList<>();

    private final Gson gson;

    private TicketService ticketService;

    private TicketBucketService ticketBucketService;

    @Autowired
    @Lazy
    private PartnerService self;

    public PartnerService(Gson gson) {
        this.gson = gson;
    }

    @Lazy
    @Autowired
    private void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Lazy
    @Autowired
    private void setTicketBucketService(TicketBucketService ticketBucketService) {
        this.ticketBucketService = ticketBucketService;
    }

    @PostConstruct
    private void init() {
        this.functions.addAll(super.getCommonFunctions("Partner", Partner.class, gson));

        // CreateRequest
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Partner",
                "CreateRequest",
                SchemaUtil.ArgSpec.ofRef("partnerRequest", PartnerRequest.class),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::createRequest));

        // GetLoggedInPartner
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Partner",
                "GetLoggedInPartner",
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::getLoggedInPartner));

        // UpdateLoggedInPartnerVerificationStatus
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Partner",
                "UpdateLoggedInPartnerVerificationStatus",
                SchemaUtil.ArgSpec.ofRef("status", PartnerVerificationStatus.class),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::updateLoggedInPartnerVerificationStatus));

        // ToggleLoggedInPartnerDnc
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Partner",
                "ToggleLoggedInPartnerDnc",
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::toggleLoggedInPartnerDnc));

        // UpdatePartnerVerificationStatus
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Partner",
                "UpdatePartnerVerificationStatus",
                SchemaUtil.ArgSpec.identity("partnerId"),
                SchemaUtil.ArgSpec.ofRef("status", PartnerVerificationStatus.class),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::updatePartnerVerificationStatus));

        // TogglePartnerDnc
        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "Partner",
                "TogglePartnerDnc",
                SchemaUtil.ArgSpec.identity("partnerId"),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::togglePartnerDnc));
    }

    @Override
    protected String getCacheName() {
        return PARTNER_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<Boolean> evictCache(Partner entity) {
        return Mono.zip(
                        super.evictCache(entity),
                        super.cacheService.evict(
                                this.getCacheName(),
                                super.getCacheKey(entity.getAppCode(), entity.getClientCode(), entity.getClientId())),
                        (baseEvicted, cIdEvicted) -> baseEvicted && cIdEvicted)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.evictCache"));
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PARTNER;
    }

    @Override
    protected Mono<Partner> updatableEntity(Partner entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setManagerId(entity.getManagerId());
                    existing.setPartnerVerificationStatus(entity.getPartnerVerificationStatus());
                    existing.setDnc(entity.getDnc());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.updatableEntity"));
    }

    @Override
    public Mono<ProcessorAccess> hasAccess() {
        return FlatMapUtil.flatMapMono(super::hasAccess, access -> {
                    if (!access.isHasBpAccess())
                        return super.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.PARTNER_ACCESS_DENIED);

                    return Mono.just(access);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.hasAccess"));
    }

    public Mono<Partner> createRequest(PartnerRequest partnerRequest) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.securityService.getClientById(
                                partnerRequest.getClientId().toBigInteger()),
                        (access, client) -> {
                            if (!client.getLevelType().equals(BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP))
                                return super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.INVALID_CLIENT_TYPE,
                                        client.getLevelType(),
                                        this.getEntityName(),
                                        BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP);

                            return super.createInternal(
                                    access,
                                    Partner.of(partnerRequest)
                                            .setManagerId(access.getUserId())
                                            .setPartnerVerificationStatus(PartnerVerificationStatus.INVITATION_SENT));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.createPartner"));
    }

    public Mono<Partner> getLoggedInPartner() {
        return super.hasAccess()
                .flatMap(this::getLoggedInPartner)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getLoggedInPartner"));
    }

    private Mono<Partner> getLoggedInPartner(ProcessorAccess access) {
        return this.getPartnerByClientId(
                access, ULongUtil.valueOf(access.getUser().getClientId()));
    }

    public Mono<Partner> updateLoggedInPartnerVerificationStatus(PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                        this::getLoggedInPartner,
                        partner -> super.updateInternalForOutsideUser(partner.setPartnerVerificationStatus(status)),
                        (partner, uPartner) -> this.evictCache(partner).map(evicted -> uPartner))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "PartnerService.updateLoggedInPartnerVerificationStatus"));
    }

    public Mono<Partner> toggleLoggedInPartnerDnc() {
        return FlatMapUtil.flatMapMono(
                        this::getLoggedInPartner,
                        partner -> super.hasAccess(),
                        (partner, access) -> super.updateInternalForOutsideUser(partner.setDnc(!partner.getDnc())),
                        (partner, access, uPartner) -> this.evictCache(partner),
                        (partner, access, uPartner, evicted) -> this.ticketService
                                .updateTicketDncByClientId(access, partner.getClientId(), !partner.getDnc())
                                .then(Mono.just(uPartner)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.toggleLoggedInPartnerDnc"));
    }

    public Mono<Partner> updatePartnerVerificationStatus(Identity partnerId, PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.readByIdentity(access, partnerId),
                        (access, partner) -> super.updateInternal(access, partner.setPartnerVerificationStatus(status)),
                        (access, partner, updated) -> this.evictCache(partner).map(evicted -> updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.updatePartnerVerificationStatus"));
    }

    public Mono<Partner> togglePartnerDnc(Identity partnerId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.readByIdentity(access, partnerId),
                        (access, partner) -> super.updateInternal(access, partner.setDnc(!partner.getDnc())),
                        (access, partner, updated) -> this.evictCache(partner),
                        (access, partner, updated, evicted) -> this.ticketService
                                .updateTicketDncByClientId(access, partner.getClientId(), !partner.getDnc())
                                .then(Mono.just(updated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.togglePartnerDnc"));
    }

    private Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.getPartnerByClientId(access, clientId),
                        super.getCacheKey(access.getAppCode(), access.getEffectiveClientCode(), clientId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getPartnerByClientId"));
    }

    public Mono<Boolean> getPartnerDnc(ProcessorAccess access) {

        if (!access.isOutsideUser()) return Mono.just(Boolean.FALSE);

        return this.getPartnerByClientId(
                        access, ULongUtil.valueOf(access.getUser().getClientId()))
                .map(partner -> partner.getDnc() != null && partner.getDnc())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getPartnerDnc"));
    }

    public Mono<Page<Map<String, Object>>> readPartnerClient(Query query, MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.securityService
                                .getManagingClientIds(access.getUser().getClientId())
                                .map(ids -> ids.stream().map(ULongUtil::valueOf).toList()),
                        (access, clientIds) -> this.addClientConditions(query.getCondition(), clientIds),
                        (access, clientIds, clientCondition) ->
                                this.getPartners(query.getCondition(), access, clientIds),
                        (access, clientIds, clientCondition, partners) -> this.updateClientCondition(
                                clientCondition,
                                partners.stream().map(Partner::getClientId).toList()),
                        (access, clientIds, clientCondition, partners, uClientCondition) -> super.securityService
                                .readClientPageFilterInternal(
                                        this.updateQueryCondition(query, uClientCondition), queryParams)
                                .map(page -> page.map(IClassConvertor::toMap)),
                        (access, clientIds, clientCondition, partners, uClientCondition, clientPage) ->
                                this.fillClientDetails(access, partners, clientPage.getContent(), queryParams)
                                        .thenReturn(clientPage))
                .switchIfEmpty(Mono.just(Page.empty()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.readPartnerClient"));
    }

    private Mono<List<Partner>> getPartners(
            AbstractCondition condition, ProcessorAccess access, List<ULong> clientIds) {

        if (clientIds == null || clientIds.isEmpty()) return Mono.empty();

        if (condition == null || condition.isEmpty())
            return this.dao
                    .getPartners(access, clientIds)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getPartners"));

        return FlatMapUtil.flatMapMono(
                        () -> condition
                                .findAndCreatePrefix(this.getEntityPrefix(access.getAppCode()))
                                .collectList(),
                        conditions -> this.dao.getPartners(
                                ComplexCondition.and(conditions.toArray(new AbstractCondition[0])), access, clientIds))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getPartners"));
    }

    public Mono<Page<Map<String, Object>>> readPartnerTeammates(
            Identity partnerId, Query query, MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.readByIdentity(access, partnerId),
                        (access, partner) -> this.addClientIds(partner, query.getCondition()),
                        (access, partner, pCondition) -> super.securityService
                                .readUserPageFilterInternal(this.updateQueryCondition(query, pCondition), queryParams)
                                .map(page -> page.map(IClassConvertor::toMap)),
                        (access, partner, pCondition, userPage) -> this.fillUserDetails(
                                        access, userPage.getContent(), queryParams)
                                .thenReturn(userPage))
                .switchIfEmpty(Mono.just(Page.empty()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.readPartnerTeammates"));
    }

    public Mono<Page<Map<String, Object>>> readLoggedInPartnerTeammates(
            Query query, MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        this::getLoggedInPartner,
                        (access, partner) -> this.addClientIds(partner, query.getCondition()),
                        (access, partner, pCondition) -> super.securityService
                                .readUserPageFilterInternal(updateQueryCondition(query, pCondition), queryParams)
                                .map(page -> page.map(IClassConvertor::toMap)),
                        (access, partner, pCondition, userPage) -> this.fillUserDetails(
                                        access, userPage.getContent(), queryParams)
                                .thenReturn(userPage))
                .switchIfEmpty(Mono.just(Page.empty()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.readLoggedInPartnerTeammates"));
    }

    private Mono<Collection<Map<String, Object>>> fillClientDetails(
            ProcessorAccess access,
            List<Partner> partners,
            List<Map<String, Object>> clients,
            MultiValueMap<String, String> queryParams) {

        Map<ULong, Map<String, Object>> clientMapById = clients.stream()
                .collect(Collectors.toMap(c -> ULongUtil.valueOf(c.get(AbstractDTO.Fields.id)), Function.identity()));

        return FlatMapUtil.flatMapMono(
                () -> this.fillPartnerTicketDetails(access, partners, clientMapById, queryParams),
                ticketClients -> this.fillPartnerDetails(partners, clientMapById, queryParams));
    }

    private Mono<Collection<Map<String, Object>>> fillUserDetails(
            ProcessorAccess access, List<Map<String, Object>> users, MultiValueMap<String, String> queryParams) {

        Map<ULong, Map<String, Object>> userMapById = users.stream()
                .collect(Collectors.toMap(c -> ULongUtil.valueOf(c.get(AbstractDTO.Fields.id)), Function.identity()));

        return this.fillPartnerTeammateTicketDetails(access, userMapById, queryParams);
    }

    private Mono<Collection<Map<String, Object>>> fillPartnerDetails(
            List<Partner> partners,
            Map<ULong, Map<String, Object>> clientMapById,
            MultiValueMap<String, String> queryParams) {

        boolean fetchPartner = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_PARTNERS));

        if (!fetchPartner || clientMapById.isEmpty()) return Mono.just(clientMapById.values());

        String partnerEntityKey = this.getEntityKey();

        partners.forEach(partner -> {
            Map<String, Object> clientMap = clientMapById.get(partner.getClientId());
            if (clientMap != null) clientMap.put(partnerEntityKey, partner.toMap());
        });
        return Mono.just(clientMapById.values());
    }

    private Mono<Collection<Map<String, Object>>> fillPartnerTicketDetails(
            ProcessorAccess access,
            List<Partner> partners,
            Map<ULong, Map<String, Object>> clientMapById,
            MultiValueMap<String, String> queryParams) {

        boolean fetchPartner = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_LEADS));

        boolean includeZero = BooleanUtil.safeValueOf(queryParams.getFirst(BaseFilter.Fields.includeZero));
        boolean includePercentage = BooleanUtil.safeValueOf(queryParams.getFirst(BaseFilter.Fields.includePercentage));
        boolean includeTotal = BooleanUtil.safeValueOf(queryParams.getFirst(BaseFilter.Fields.includeTotal));
        boolean includeAll = BooleanUtil.safeValueOf(queryParams.getFirst(TicketBucketFilter.Fields.includeAll));
        boolean includeNone = BooleanUtil.safeValueOf(queryParams.getFirst(TicketBucketFilter.Fields.includeNone));

        List<ULong> stages = queryParams.getOrDefault(TicketBucketFilter.Fields.stageIds, List.of()).stream()
                .map(ULongUtil::valueOf)
                .toList();

        if (!fetchPartner || clientMapById.isEmpty()) return Mono.just(clientMapById.values());

        Map<ULong, IdAndValue<ULong, String>> clientFilterMap = clientMapById.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        client -> IdAndValue.of(
                                client.getKey(),
                                client.getValue().get(Client.Fields.name).toString()),
                        (a, b) -> b));

        TicketBucketFilter filter = (TicketBucketFilter) new TicketBucketFilter()
                .setStageIds(stages)
                .setClientIds(clientMapById.keySet().stream().toList())
                .setClients(clientFilterMap.values().stream().toList())
                .setIncludeAll(includeAll)
                .setIncludeNone(includeNone)
                .setIncludeZero(includeZero)
                .setIncludePercentage(includePercentage)
                .setIncludeTotal(includeTotal);

        return FlatMapUtil.flatMapMono(
                () -> ticketBucketService.getTicketPerClientIdStageCount(access, filter), statusCounts -> {
                    Map<ULong, StatusEntityCount> status = statusCounts.stream()
                            .collect(Collectors.toMap(StatusEntityCount::getId, Function.identity()));

                    String ticketKey = ticketBucketService.getEntityKey();

                    partners.forEach(partner -> {
                        StatusEntityCount count = status.get(partner.getClientId());
                        Map<String, Object> clientMap = clientMapById.get(partner.getClientId());
                        if (count != null && clientMap != null) clientMap.put(ticketKey, count.toMap());
                    });

                    return Mono.just(clientMapById.values());
                });
    }

    private Mono<Collection<Map<String, Object>>> fillPartnerTeammateTicketDetails(
            ProcessorAccess access,
            Map<ULong, Map<String, Object>> userMapByIds,
            MultiValueMap<String, String> queryParams) {

        boolean fetchPartner = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_LEADS));

        boolean includeZero = BooleanUtil.safeValueOf(queryParams.getFirst(BaseFilter.Fields.includeZero));
        boolean includePercentage = BooleanUtil.safeValueOf(queryParams.getFirst(BaseFilter.Fields.includePercentage));
        boolean includeTotal = BooleanUtil.safeValueOf(queryParams.getFirst(BaseFilter.Fields.includeTotal));
        boolean includeAll = BooleanUtil.safeValueOf(queryParams.getFirst(TicketBucketFilter.Fields.includeAll));
        boolean includeNone = BooleanUtil.safeValueOf(queryParams.getFirst(TicketBucketFilter.Fields.includeNone));

        List<ULong> stages = queryParams.getOrDefault(TicketBucketFilter.Fields.stageIds, List.of()).stream()
                .map(ULongUtil::valueOf)
                .toList();

        if (!fetchPartner || userMapByIds.isEmpty()) return Mono.just(userMapByIds.values());

        Map<ULong, IdAndValue<ULong, String>> userFilterMap = userMapByIds.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        user -> IdAndValue.of(
                                user.getKey(),
                                NameUtil.assembleFullName(
                                        user.getValue().get(User.Fields.firstName),
                                        user.getValue().get(User.Fields.middleName),
                                        user.getValue().get(User.Fields.lastName))),
                        (a, b) -> b));

        TicketBucketFilter filter = (TicketBucketFilter) new TicketBucketFilter()
                .setStageIds(stages)
                .setCreatedByIds(userMapByIds.keySet().stream().toList())
                .setCreatedBys(userFilterMap.values().stream().toList())
                .setIncludeAll(includeAll)
                .setIncludeNone(includeNone)
                .setIncludeZero(includeZero)
                .setIncludePercentage(includePercentage)
                .setIncludeTotal(includeTotal);

        return FlatMapUtil.flatMapMono(
                () -> ticketBucketService.getTicketPerCreatedByStageCount(access, filter), statusCounts -> {
                    Map<ULong, StatusEntityCount> status = statusCounts.stream()
                            .collect(Collectors.toMap(StatusEntityCount::getId, Function.identity()));

                    String ticketKey = ticketBucketService.getEntityKey();

                    userMapByIds.forEach((key, value) -> {
                        StatusEntityCount count = status.get(ULongUtil.valueOf(key));
                        if (count != null) value.put(ticketKey, count.toMap());
                    });

                    return Mono.just(userMapByIds.values());
                });
    }

    private Query updateQueryCondition(Query query, AbstractCondition condition) {
        if (condition == null || condition.isEmpty()) return query;

        query.setCount(Boolean.FALSE);

        if (query.getCondition() == null || query.getCondition().isEmpty()) {
            query.setCondition(condition);
            return query;
        }

        return query.setCondition(ComplexCondition.and(query.getCondition(), condition));
    }

    private Mono<AbstractCondition> addClientIds(Partner partner, AbstractCondition condition) {

        if (condition == null || condition.isEmpty())
            return Mono.<AbstractCondition>just(FilterCondition.make(User.Fields.clientId, partner.getClientId())
                            .setOperator(FilterConditionOperator.EQUALS))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.addClientIds"));

        return Mono.<AbstractCondition>just(ComplexCondition.and(
                        condition,
                        FilterCondition.make(User.Fields.clientId, partner.getClientId())
                                .setOperator(FilterConditionOperator.EQUALS)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.addClientIds"));
    }

    private Mono<AbstractCondition> updateClientCondition(AbstractCondition condition, List<ULong> clientIds) {

        return FlatMapUtil.flatMapMono(
                        () -> condition.removeConditionWithField(AbstractDTO.Fields.id),
                        conditions -> Mono.<AbstractCondition>just(ComplexCondition.and(
                                conditions,
                                new FilterCondition()
                                        .setField(AbstractDTO.Fields.id)
                                        .setOperator(FilterConditionOperator.IN)
                                        .setMultiValue(clientIds))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.updateClientCondition"));
    }

    private Mono<AbstractCondition> addClientConditions(AbstractCondition condition, List<ULong> clientIds) {

        if (clientIds == null || clientIds.isEmpty())
            return Mono.<AbstractCondition>empty()
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.addClientConditions"));

        if (condition == null || condition.isEmpty())
            return Mono.<AbstractCondition>just(ComplexCondition.and(
                            FilterCondition.make(Client.Fields.levelType, BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP),
                            new FilterCondition()
                                    .setField(AbstractDTO.Fields.id)
                                    .setOperator(FilterConditionOperator.IN)
                                    .setMultiValue(clientIds)))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.addClientConditions"));

        return Mono.<AbstractCondition>just(ComplexCondition.and(
                        condition,
                        FilterCondition.make(Client.Fields.levelType, BusinessPartnerConstant.CLIENT_LEVEL_TYPE_BP),
                        new FilterCondition()
                                .setField(AbstractDTO.Fields.id)
                                .setOperator(FilterConditionOperator.IN)
                                .setMultiValue(clientIds)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.addClientConditions"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        // Generate schema for Partner class and create a repository with it
        Map<String, Schema> partnerSchemas = new HashMap<>();

        // TODO: When we add dynamic fields, the schema will be generated dynamically from DB.
        try {
            Class<?> partnerClass = Partner.class;

            String namespace = SchemaUtil.getNamespaceForClass(partnerClass);
            String name = partnerClass.getSimpleName();

            Schema schema = SchemaUtil.generateSchemaForClass(partnerClass);
            if (schema != null) {
                partnerSchemas.put(namespace + "." + name, schema);
                LOGGER.info("Generated schema for Partner class: {}.{}", namespace, name);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate schema for Partner class: {}", e.getMessage(), e);
        }

        // If we have Partner schema, create a repository with it
        if (!partnerSchemas.isEmpty()) {
            return Mono.just(new MapSchemaRepository(partnerSchemas));
        }

        return Mono.empty();
    }
}
