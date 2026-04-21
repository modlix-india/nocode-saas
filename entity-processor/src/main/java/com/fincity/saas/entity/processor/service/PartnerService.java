package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractServiceFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
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
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorPartnersRecord;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.PartnerRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.EntityProcessorArgSpec;
import com.fincity.saas.entity.processor.util.NameUtil;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class PartnerService extends BaseUpdatableService<EntityProcessorPartnersRecord, Partner, PartnerDAO>
        implements IRepositoryProvider {

    private static final String NAMESPACE = "EntityProcessor.Partner";

    private static final String FETCH_LEADS = "fetchLeads";

    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;
    private TicketService ticketService;

    private TicketBucketService ticketBucketService;

    @Lazy
    @Autowired
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
        this.functions.addAll(super.getCommonFunctions(NAMESPACE, Partner.class, classSchema, gson));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "CreateRequest",
                ClassSchema.ArgSpec.ofRef("partnerRequest", PartnerRequest.class, classSchema),
                "created",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::createRequest));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "GetLoggedInPartner",
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::getLoggedInPartner));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "UpdateLoggedInPartnerVerificationStatus",
                ClassSchema.ArgSpec.ofRef("status", PartnerVerificationStatus.class, classSchema),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::updateLoggedInPartnerVerificationStatus));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "ToggleLoggedInPartnerDnc",
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::toggleLoggedInPartnerDnc));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "UpdatePartnerVerificationStatus",
                EntityProcessorArgSpec.identity("partnerId"),
                ClassSchema.ArgSpec.ofRef("status", PartnerVerificationStatus.class, classSchema),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::updatePartnerVerificationStatus));

        this.functions.add(AbstractServiceFunction.createServiceFunction(
                NAMESPACE,
                "TogglePartnerDnc",
                EntityProcessorArgSpec.identity("partnerId"),
                "result",
                Schema.ofRef("EntityProcessor.DTO.Partner"),
                gson,
                self::togglePartnerDnc));
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }


    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PARTNER;
    }

    @Override
    protected Mono<Partner> updatableEntity(Partner entity) {
        return FlatMapUtil.flatMapMono(() -> this.readByIdInternal(entity.getId()), existing -> {
                    existing.setDescription(entity.getDescription());
                    existing.setTempActive(entity.isTempActive());
                    existing.setActive(entity.isActive());
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
                                            .setClientName(client.getName())
                                            .setManagerId(null)
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
                        (partner, uPartner) -> Mono.just(uPartner))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "PartnerService.updateLoggedInPartnerVerificationStatus"));
    }

    public Mono<Partner> toggleLoggedInPartnerDnc() {
        return FlatMapUtil.flatMapMono(
                        this::getLoggedInPartner,
                        partner -> super.hasAccess(),
                        (partner, access) -> super.updateInternalForOutsideUser(partner.setDnc(!partner.getDnc())),
                        (partner, access, uPartner) -> this.ticketService
                                .updateTicketDncByClientId(uPartner.getClientId(), uPartner.getDnc())
                                .then(Mono.just(uPartner)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.toggleLoggedInPartnerDnc"));
    }

    public Mono<Partner> updatePartnerVerificationStatus(Identity partnerId, PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.readByIdentity(access, partnerId),
                        (access, partner) -> super.updateInternal(access, partner.setPartnerVerificationStatus(status)),
                        (access, partner, updated) -> Mono.just(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.updatePartnerVerificationStatus"));
    }

    public Mono<Partner> togglePartnerDnc(Identity partnerId) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> super.readByIdentity(access, partnerId),
                        (access, partner) -> super.updateInternal(access, partner.setDnc(!partner.getDnc())),
                        (access, partner, updated) -> this.ticketService
                                .updateTicketDncByClientId(updated.getClientId(), updated.getDnc())
                                .then(Mono.just(updated)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.togglePartnerDnc"));
    }

    private Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
        return this.dao.getPartnerByClientId(access, clientId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getPartnerByClientId"));
    }

    public Mono<Partner> read(ULong id, MultiValueMap<String, String> queryParams) {
        return super.read(id)
                .flatMap(partner -> this.enrichPartners(List.of(partner), queryParams).thenReturn(partner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.read"));
    }

    public Mono<Page<Partner>> readPageFilter(
            Pageable pageable, AbstractCondition condition, MultiValueMap<String, String> queryParams) {
        return super.readPageFilter(pageable, condition)
                .flatMap(page -> this.enrichPartners(page.getContent(), queryParams).thenReturn(page))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.readPageFilter"));
    }

    private Mono<Void> enrichPartners(List<Partner> partners, MultiValueMap<String, String> queryParams) {
        if (partners == null || partners.isEmpty()) return Mono.empty();
        return Mono.when(this.fillClients(partners, queryParams), this.fillAuditUsers(partners));
    }

    private Mono<Void> fillClients(List<Partner> partners, MultiValueMap<String, String> queryParams) {
        List<BigInteger> ids = partners.stream()
                .map(Partner::getClientId)
                .filter(Objects::nonNull)
                .map(ULong::toBigInteger)
                .distinct()
                .toList();

        if (ids.isEmpty()) return Mono.empty();

        MultiValueMap<String, String> params = queryParams != null ? queryParams : new LinkedMultiValueMap<>();

        return super.securityService
                .getClientInternalBatch(ids, params)
                .doOnNext(clients -> {
                    Map<BigInteger, Client> byId = clients.stream()
                            .collect(Collectors.toMap(Client::getId, Function.identity(), (a, b) -> a));
                    partners.forEach(p -> {
                        if (p.getClientId() != null)
                            p.setClientInfo(byId.get(p.getClientId().toBigInteger()));
                    });
                })
                .then();
    }

    private Mono<Void> fillAuditUsers(List<Partner> partners) {
        Set<BigInteger> ids = new HashSet<>();
        partners.forEach(p -> {
            if (p.getCreatedBy() != null) ids.add(p.getCreatedBy().toBigInteger());
            if (p.getUpdatedBy() != null) ids.add(p.getUpdatedBy().toBigInteger());
        });

        if (ids.isEmpty()) return Mono.empty();

        return super.securityService
                .getUsersInternalBatch(new ArrayList<>(ids), new LinkedMultiValueMap<>())
                .doOnNext(users -> {
                    Map<BigInteger, User> byId = users.stream()
                            .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
                    partners.forEach(p -> {
                        if (p.getCreatedBy() != null)
                            p.setCreatedByUser(byId.get(p.getCreatedBy().toBigInteger()));
                        if (p.getUpdatedBy() != null)
                            p.setUpdatedByUser(byId.get(p.getUpdatedBy().toBigInteger()));
                    });
                })
                .then();
    }

    public Mono<Boolean> getPartnerDnc(ProcessorAccess access) {

        if (!access.isOutsideUser()) return Mono.just(Boolean.FALSE);

        return this.getPartnerByClientId(
                        access, ULongUtil.valueOf(access.getUser().getClientId()))
                .map(partner -> partner.getDnc() != null && partner.getDnc())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.getPartnerDnc"));
    }

//     @Override
//     public Mono<Page<Partner>> readPageFilter(Pageable pageable, AbstractCondition condition) {

//         return FlatMapUtil.flatMapMono(
//                         this::hasAccess,
//                         access -> Mono.justOrEmpty(access.getUserInherit().getManagingClientIds())
//                                 .defaultIfEmpty(List.of()),
//                         (access, clientIds) -> {
//                             if (clientIds.isEmpty()) return Mono.just(Page.<Partner>empty());

//                             AbstractCondition scopeCondition = new FilterCondition()
//                                     .setField(Partner.Fields.clientId)
//                                     .setOperator(FilterConditionOperator.IN)
//                                     .setMultiValue(clientIds);

//                             AbstractCondition fullCondition = condition == null || condition.isEmpty()
//                                     ? scopeCondition
//                                     : ComplexCondition.and(condition, scopeCondition);

//                             return this.dao.processorAccessCondition(fullCondition, access);
//                         },
//                         (access, clientIds, pCondition) -> super.readPageFilter(pageable, pCondition))
//                 .switchIfEmpty(Mono.just(Page.empty()))
//                 .contextWrite(Context.of(LogUtil.METHOD_NAME, "PartnerService.readPageFilter"));
//     }

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

    private Mono<Collection<Map<String, Object>>> fillUserDetails(
            ProcessorAccess access, List<Map<String, Object>> users, MultiValueMap<String, String> queryParams) {

        Map<ULong, Map<String, Object>> userMapById = users.stream()
                .collect(Collectors.toMap(c -> ULongUtil.valueOf(c.get(AbstractDTO.Fields.id)), Function.identity()));

        return this.fillPartnerTeammateTicketDetails(access, userMapById, queryParams);
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

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(Partner.class, classSchema);
    }
}
