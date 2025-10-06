package com.fincity.saas.entity.processor.service;

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
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.NameUtil;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
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
        implements IEntitySeries {

    private static final String PARTNER_CACHE = "Partner";

    private static final String FETCH_PARTNERS = "fetchPartners";

    private static final String FETCH_LEADS = "fetchLeads";

    private TicketService ticketService;

    private TicketBucketService ticketBucketService;

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

    public Mono<Partner> createPartner(PartnerRequest partnerRequest) {
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
                        partner -> super.update(partner.setPartnerVerificationStatus(status)),
                        (partner, uPartner) -> this.evictCache(partner).map(evicted -> uPartner))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "PartnerService.updateLoggedInPartnerVerificationStatus"));
    }

    public Mono<Partner> toggleLoggedInPartnerDnc() {
        return FlatMapUtil.flatMapMono(
                        this::getLoggedInPartner,
                        partner -> super.update(partner.setDnc(!partner.getDnc())),
                        (partner, uPartner) -> this.evictCache(partner),
                        (partner, uPartner, evicted) -> this.ticketService
                                .updateTicketDncByClientId(partner.getClientId(), !partner.getDnc())
                                .then(Mono.just(uPartner)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.toggleLoggedInPartnerDnc"));
    }

    public Mono<Partner> updatePartnerVerificationStatus(Identity partnerId, PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.readIdentityWithAccess(access, partnerId),
                        (access, partner) -> super.updateInternal(access, partner.setPartnerVerificationStatus(status)),
                        (access, partner, updated) -> this.evictCache(partner).map(evicted -> updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.updatePartnerVerificationStatus"));
    }

    public Mono<Partner> togglePartnerDnc(Identity partnerId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.readIdentityWithAccess(access, partnerId),
                        (access, partner) -> super.updateInternal(access, partner.setDnc(!partner.getDnc())),
                        (access, partner, updated) -> this.evictCache(partner),
                        (access, partner, updated, evicted) -> this.ticketService
                                .updateTicketDncByClientId(partner.getClientId(), !partner.getDnc())
                                .then(Mono.just(updated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.togglePartnerDnc"));
    }

    public Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
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
                        access -> this.readIdentityWithAccess(access, partnerId),
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

    public Mono<AbstractCondition> addClientIds(Partner partner, AbstractCondition condition) {

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

    public Mono<AbstractCondition> updateClientCondition(AbstractCondition condition, List<ULong> clientIds) {

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

    public Mono<AbstractCondition> addClientConditions(AbstractCondition condition, List<ULong> clientIds) {

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
}
