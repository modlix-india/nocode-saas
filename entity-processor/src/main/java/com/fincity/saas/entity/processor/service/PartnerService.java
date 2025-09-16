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
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.dao.PartnerDAO;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.PartnerVerificationStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
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

@Service
public class PartnerService extends BaseUpdatableService<EntityProcessorPartnersRecord, Partner, PartnerDAO>
        implements IEntitySeries {

    private static final String PARTNER_CACHE = "Partner";

    private static final String FETCH_PARTNERS = "fetchPartners";

    private TicketService ticketService;

    @Lazy
    @Autowired
    private void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
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
                (baseEvicted, cIdEvicted) -> baseEvicted && cIdEvicted);
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PARTNER;
    }

    @Override
    public Mono<Partner> update(ULong key, Map<String, Object> fields) {
        return super.update(key, fields)
                .flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    @Override
    public Mono<Partner> update(Partner entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(updated).map(evicted -> updated));
    }

    @Override
    protected Mono<Partner> updatableEntity(Partner entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setManagerId(entity.getManagerId());
            existing.setPartnerVerificationStatus(entity.getPartnerVerificationStatus());
            existing.setDnc(entity.getDnc());
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return super.delete(id);
    }

    @Override
    public Mono<ProcessorAccess> hasAccess() {
        return FlatMapUtil.flatMapMono(super::hasAccess, access -> {
            if (!access.isHasBpAccess())
                return super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.PARTNER_ACCESS_DENIED);

            return Mono.just(access);
        });
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
                });
    }

    public Mono<Partner> getLoggedInPartner() {
        return this.hasAccess().flatMap(this::getLoggedInPartner);
    }

    private Mono<Partner> getLoggedInPartner(ProcessorAccess access) {
        return this.getPartnerByClientId(
                access, ULongUtil.valueOf(access.getUser().getClientId()));
    }

    public Mono<Partner> updateLoggedInPartnerVerificationStatus(PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                this::getLoggedInPartner,
                partner -> super.update(partner.setPartnerVerificationStatus(status)),
                (partner, uPartner) -> this.evictCache(partner).map(evicted -> uPartner));
    }

    public Mono<Partner> toggleLoggedInPartnerDnc() {
        return FlatMapUtil.flatMapMono(
                this::getLoggedInPartner,
                partner -> super.update(partner.setDnc(!partner.getDnc())),
                (partner, uPartner) -> this.evictCache(partner),
                (partner, uPartner, evicted) -> this.ticketService
                        .updateTicketDncByClientId(partner.getClientId(), !partner.getDnc())
                        .then(Mono.just(uPartner)));
    }

    public Mono<Partner> updatePartnerVerificationStatus(Identity partnerId, PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> super.readIdentityWithAccess(access, partnerId),
                (access, partner) -> super.updateInternal(access, partner.setPartnerVerificationStatus(status)),
                (access, partner, updated) -> this.evictCache(partner).map(evicted -> updated));
    }

    public Mono<Partner> togglePartnerDnc(Identity partnerId) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> super.readIdentityWithAccess(access, partnerId),
                (access, partner) -> super.updateInternal(access, partner.setDnc(!partner.getDnc())),
                (access, partner, updated) -> this.evictCache(partner),
                (access, partner, updated, evicted) -> this.ticketService
                        .updateTicketDncByClientId(partner.getClientId(), !partner.getDnc())
                        .then(Mono.just(updated)));
    }

    public Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getPartnerByClientId(access, clientId),
                super.getCacheKey(access.getAppCode(), access.getEffectiveClientCode(), clientId));
    }

    public Mono<Boolean> getPartnerDnc(ProcessorAccess access) {

        if (!access.isOutsideUser()) return Mono.just(Boolean.FALSE);

        return this.getPartnerByClientId(
                        access, ULongUtil.valueOf(access.getUser().getClientId()))
                .map(partner -> partner.getDnc() != null && partner.getDnc());
    }

    public Mono<Page<Map<String, Object>>> readPartnerClient(Query query, MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.addManagingClientIds(access, query.getCondition()),
                (access, pCondition) -> super.securityService
                        .readClientPageFilterInternal(this.updateQueryCondition(query, pCondition), queryParams)
                        .map(page -> page.map(IClassConvertor::toMap)),
                (access, pCondition, clientPage) -> this.fillDetails(access, clientPage.getContent(), queryParams)
                        .thenReturn(clientPage));
    }

    public Mono<Page<Map<String, Object>>> readPartnerTeammates(
            Identity partnerId, Query query, MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.readIdentityWithAccess(access, partnerId),
                (access, partner) -> this.addClientIds(partner, query.getCondition()),
                (access, partner, pCondition) -> super.securityService
                        .readUserPageFilterInternal(this.updateQueryCondition(query, pCondition), queryParams)
                        .map(page -> page.map(IClassConvertor::toMap)));
    }

    public Mono<Page<Map<String, Object>>> readLoggedInPartnerTeammates(
            Query query, MultiValueMap<String, String> queryParams) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                this::getLoggedInPartner,
                (access, partner) -> this.addClientIds(partner, query.getCondition()),
                (access, partner, pCondition) -> super.securityService
                        .readUserPageFilterInternal(updateQueryCondition(query, pCondition), queryParams)
                        .map(page -> page.map(IClassConvertor::toMap)));
    }

    private Mono<List<Map<String, Object>>> fillDetails(
            ProcessorAccess access, List<Map<String, Object>> clients, MultiValueMap<String, String> queryParams) {

        return Mono.defer(() -> Mono.just(clients)).flatMap(c -> fillPartnerDetails(access, c, queryParams));
    }

    private Mono<List<Map<String, Object>>> fillPartnerDetails(
            ProcessorAccess access, List<Map<String, Object>> clients, MultiValueMap<String, String> queryParams) {

        boolean fetchPartner = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_PARTNERS));

        if (!fetchPartner || clients.isEmpty()) return Mono.just(clients);

        Map<ULong, Map<String, Object>> clientMapById = clients.stream()
                .collect(Collectors.toMap(
                        c -> ULongUtil.valueOfDouble(c.get(AbstractDTO.Fields.id)), Function.identity()));

        return this.dao
                .getPartnerByClientIds(access, clientMapById.keySet().stream().toList())
                .map(partners -> {
                    partners.forEach(partner -> {
                        Map<String, Object> clientMap = clientMapById.get(partner.getClientId());
                        if (clientMap != null) clientMap.put(this.getEntityKey(), partner.toMap());
                    });
                    return clients;
                })
                .switchIfEmpty(Mono.just(clients));
    }

    private Query updateQueryCondition(Query query, AbstractCondition condition) {
        if (condition == null || condition.isEmpty()) return query;

        if (query.getCondition() == null || query.getCondition().isEmpty()) {
            query.setCondition(condition);
            return query;
        }

        return query.setCondition(ComplexCondition.and(query.getCondition(), condition));
    }

    public Mono<AbstractCondition> addClientIds(Partner partner, AbstractCondition condition) {

        if (condition == null || condition.isEmpty())
            return Mono.just(FilterCondition.make(User.Fields.clientId, partner.getClientId())
                    .setMatchOperator(FilterConditionOperator.EQUALS));

        return Mono.just(ComplexCondition.and(
                condition,
                FilterCondition.make(User.Fields.clientId, partner.getClientId())
                        .setMatchOperator(FilterConditionOperator.EQUALS)));
    }

    public Mono<AbstractCondition> addManagingClientIds(ProcessorAccess access, AbstractCondition condition) {

        return FlatMapUtil.flatMapMono(
                () -> super.securityService.getManagingClientIds(
                        access.getUser().getClientId()),
                clientIds -> {
                    if (clientIds == null || clientIds.isEmpty()) return Mono.empty();

                    if (condition == null || condition.isEmpty())
                        return Mono.just(new FilterCondition()
                                .setField(AbstractDTO.Fields.id)
                                .setOperator(FilterConditionOperator.IN)
                                .setMultiValue(clientIds));

                    return Mono.just(ComplexCondition.and(
                            condition,
                            new FilterCondition()
                                    .setField(AbstractDTO.Fields.id)
                                    .setOperator(FilterConditionOperator.IN)
                                    .setMultiValue(clientIds)));
                });
    }
}
