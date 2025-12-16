package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.OwnerDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorOwnersRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.OwnerRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class OwnerService extends BaseProcessorService<EntityProcessorOwnersRecord, Owner, OwnerDAO> {

    private static final String OWNER_CACHE = "owner";

    private final TicketService ticketService;

    public OwnerService(@Lazy TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    protected String getCacheName() {
        return OWNER_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.TRUE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.OWNER;
    }

    @Override
    protected Mono<Owner> updatableEntity(Owner entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setDialCode(entity.getDialCode());
                    existing.setPhoneNumber(entity.getPhoneNumber());
                    existing.setEmail(entity.getEmail());

                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.updatableEntity"));
    }

    public Mono<Owner> createRequest(OwnerRequest ownerRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.checkDuplicate(access, ownerRequest),
                        (access, isDuplicate) -> super.create(access, Owner.of(ownerRequest)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.create"));
    }

    @Override
    public Mono<Owner> update(Owner entity) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.update(access, entity),
                        (access, updated) ->
                                this.updateOwnerTickets(access, updated).thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.update"));
    }

    @IgnoreGeneration
    public Mono<Owner> getOrCreateTicketOwner(ProcessorAccess access, Ticket ticket) {

        if (ticket.getOwnerId() != null)
            return this.readById(access, ULongUtil.valueOf(ticket.getOwnerId()))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.getOrCreateTicketOwner"));

        return this.getOrCreateTicketPhoneOwner(access, ticket)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.getOrCreateTicketOwner"));
    }

    private Mono<Owner> updateOwnerTickets(ProcessorAccess access, Owner owner) {
        return this.ticketService
                .updateOwnerTickets(access, owner)
                .collectList()
                .map(tickets -> owner)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.updateTickets"));
    }

    @IgnoreGeneration
    public Mono<Ticket> updateTicketOwner(ProcessorAccess access, Ticket ticket) {
        return this.readById(access, ticket.getOwnerId()).flatMap(owner -> {
            owner.setName(ticket.getName());
            owner.setEmail(ticket.getEmail());
            return this.update(access, owner).thenReturn(ticket);
        });
    }

    private Mono<Owner> getOrCreateTicketPhoneOwner(ProcessorAccess access, Ticket ticket) {
        return FlatMapUtil.flatMapMono(
                        () -> this.dao
                                .readByNumberAndEmail(
                                        access, ticket.getDialCode(), ticket.getPhoneNumber(), ticket.getEmail())
                                .switchIfEmpty(this.create(access, Owner.of(ticket))),
                        owner -> {
                            if (owner.getId() == null)
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                        ProcessorMessageResourceService.OWNER_NOT_CREATED);
                            return Mono.just(owner);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.getOrCreateTicketPhoneOwner"));
    }

    private Mono<Boolean> checkDuplicate(ProcessorAccess access, OwnerRequest ownerRequest) {
        return this.dao
                .readByNumberAndEmail(
                        access,
                        ownerRequest.getPhoneNumber() != null
                                ? ownerRequest.getPhoneNumber().getCountryCode()
                                : null,
                        ownerRequest.getPhoneNumber() != null
                                ? ownerRequest.getPhoneNumber().getNumber()
                                : null,
                        ownerRequest.getEmail() != null
                                ? ownerRequest.getEmail().getAddress()
                                : null)
                .flatMap(existing -> {
                    if (existing.getId() != null) return super.throwDuplicateError(access, existing);

                    return Mono.just(Boolean.FALSE);
                })
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OwnerService.checkDuplicate"));
    }
}
