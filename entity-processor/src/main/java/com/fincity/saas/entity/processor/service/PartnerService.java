package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
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
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PartnerService extends BaseUpdatableService<EntityProcessorPartnersRecord, Partner, PartnerDAO>
        implements IEntitySeries {

    private static final String PARTNER_CACHE = "Partner";

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
    protected Mono<Partner> updatableEntity(Partner entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setManagerId(entity.getManagerId());
            existing.setPartnerVerificationStatus(entity.getPartnerVerificationStatus());
            existing.setDnc(entity.getDnc());
            return Mono.just(existing);
        });
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
        return this.hasAccess()
                .flatMap(access -> this.getPartnerByClientId(
                        access, ULongUtil.valueOf(access.getUser().getClientId())));
    }

    public Mono<Partner> updateLoggedInPartnerVerificationStatus(PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                this::getLoggedInPartner,
                partner -> super.update(partner.setPartnerVerificationStatus(status)),
                (partner, uPartner) -> super.evictCache(partner).map(evicted -> uPartner));
    }

    public Mono<Partner> toggleLoggedInPartnerDnc() {
        return FlatMapUtil.flatMapMono(
                this::getLoggedInPartner,
                partner -> super.update(partner.setDnc(!partner.getDnc())),
                (partner, uPartner) -> super.evictCache(partner),
                (partner, uPartner, evicted) -> this.ticketService
                        .updateTicketDncByClientId(partner.getClientId(), !partner.getDnc())
                        .then(Mono.just(uPartner)));
    }

    public Mono<Partner> updatePartnerVerificationStatus(Identity partnerId, PartnerVerificationStatus status) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> super.readIdentityWithAccess(access, partnerId),
                (access, partner) -> super.updateInternal(access, partner.setPartnerVerificationStatus(status)));
    }

    public Mono<Partner> togglePartnerDnc(Identity partnerId) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> super.readIdentityWithAccess(access, partnerId),
                (access, partner) -> super.updateInternal(access, partner.setDnc(!partner.getDnc())),
                (access, partner, updated) -> this.ticketService
                        .updateTicketDncByClientId(partner.getClientId(), !partner.getDnc())
                        .then(Mono.just(updated)));
    }

    public Mono<Partner> getPartnerByClientId(ProcessorAccess access, ULong clientId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getPartnerByClientId(access, clientId),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), clientId));
    }

    public Mono<Boolean> getPartnerDnc(ProcessorAccess access) {

        if (!access.isOutsideUser()) return Mono.just(Boolean.FALSE);

        return this.getPartnerByClientId(
                        access, ULongUtil.valueOf(access.getUser().getClientId()))
                .map(partner -> partner.getDnc() != null && partner.getDnc());
    }
}
