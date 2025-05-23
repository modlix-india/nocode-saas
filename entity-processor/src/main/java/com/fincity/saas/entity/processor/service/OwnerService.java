package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.dao.OwnerDAO;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorOwnersRecord;
import com.fincity.saas.entity.processor.model.request.OwnerRequest;
import com.fincity.saas.entity.processor.service.base.BaseProcessorService;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public class OwnerService extends BaseProcessorService<EntityProcessorOwnersRecord, Owner, OwnerDAO> {

    private static final String OWNER_CACHE = "owner";

    @Override
    protected String getCacheName() {
        return OWNER_CACHE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.OWNER;
    }

    @Override
    protected Mono<Owner> checkEntity(Owner entity, Tuple3<String, String, ULong> accessInfo) {
        return Mono.just(entity);
    }

    @Override
    protected Mono<Owner> updatableEntity(Owner entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setDialCode(entity.getDialCode());
            existing.setPhoneNumber(entity.getPhoneNumber());
            existing.setEmail(entity.getEmail());

            return Mono.just(existing);
        });
    }

    public Mono<Owner> create(OwnerRequest ownerRequest) {
        return super.create(Owner.of(ownerRequest));
    }

    public Mono<Ticket> getOrCreateTicketOwner(Tuple3<String, String, ULong> accessInfo, Ticket ticket) {

        if (ticket.getOwnerId() != null)
            return FlatMapUtil.flatMapMono(() -> this.readById(ULongUtil.valueOf(ticket.getOwnerId())), model -> {
                ticket.setOwnerId(model.getId());

                if (model.getDialCode() != null && model.getPhoneNumber() != null) {
                    ticket.setDialCode(model.getDialCode());
                    ticket.setPhoneNumber(model.getPhoneNumber());
                }

                if (model.getEmail() != null) ticket.setEmail(model.getEmail());

                return Mono.just(ticket);
            });

        return this.getOrCreateTicketPhoneOwner(accessInfo.getT1(), accessInfo.getT2(), ticket);
    }

    public Mono<Ticket> getOrCreateTicketPhoneOwner(String appCode, String clientCode, Ticket ticket) {
        return FlatMapUtil.flatMapMono(
                () -> this.dao
                        .readByNumberAndEmail(
                                appCode, clientCode, ticket.getDialCode(), ticket.getPhoneNumber(), ticket.getEmail())
                        .switchIfEmpty(this.create(Owner.of(ticket))),
                model -> {
                    if (model.getId() == null)
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                ProcessorMessageResourceService.MODEL_NOT_CREATED);

                    return Mono.just(ticket.setOwnerId(model.getId()));
                });
    }
}
