package com.fincity.saas.entity.processor.service.content.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ActivityService;
import com.fincity.saas.entity.processor.service.OwnerService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.time.LocalDateTime;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class BaseContentService<
                R extends UpdatableRecord<R>, D extends BaseContentDto<D>, O extends BaseContentDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    protected TicketService ticketService;

    protected OwnerService ownerService;

    protected ActivityService activityService;

    @Autowired
    protected void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Autowired
    protected void setOwnerService(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @Lazy
    @Autowired
    protected void setActivityService(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public Mono<D> create(D entity) {
        return super.hasAccess().flatMap(access -> this.createInternal(access, entity));
    }

    public Mono<D> createPublic(D entity) {
        return super.hasPublicAccess().flatMap(access -> this.createInternal(access, entity));
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.TRUE;
    }

    @Override
    public Mono<D> createInternal(ProcessorAccess access, D entity) {
        return super.createInternal(access, entity)
                .flatMap(cContent ->
                        this.activityService.acContentCreate(cContent).then(Mono.just(cContent)));
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.VERSION_MISMATCH);

            existing.setVersion(existing.getVersion() + 1);

            existing.setContent(entity.getContent());
            existing.setHasAttachment(entity.getHasAttachment());

            if (entity.getOwnerId() != null) existing.setOwnerId(entity.getOwnerId());

            if (entity.getTicketId() != null) existing.setTicketId(entity.getTicketId());

            return Mono.just(existing);
        });
    }

    @Override
    public Mono<Integer> deleteIdentity(Identity identity) {

        return FlatMapUtil.flatMapMono(
                () -> this.readIdentityWithAccess(identity),
                entity -> super.delete(entity.getId()),
                (entity, deleted) -> this.activityService
                        .acContentDelete(entity, LocalDateTime.now())
                        .then(Mono.just(deleted)));
    }

    @Override
    public Mono<Integer> deleteByCode(String code) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.readByCode(access, code),
                (access, entity) -> super.delete(entity.getId()),
                (access, entity, deleted) -> this.activityService
                        .acContentDelete(entity, LocalDateTime.now())
                        .then(Mono.just(deleted)));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.readById(access, id),
                super::deleteInternal,
                (access, entity, deleted) -> this.activityService
                        .acContentDelete(entity, LocalDateTime.now())
                        .then(Mono.just(deleted)));
    }

    @Override
    public Mono<D> update(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.readById(access, entity.getId()).map(CloneUtil::cloneObject),
                (access, oEntity) -> this.updateInternal(entity),
                (access, oEntity, uEntity) ->
                        activityService.acContentUpdate(oEntity, uEntity).then(Mono.just(uEntity)));
    }

    public Mono<D> updateInternal(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(entity).map(evicted -> updated));
    }

    protected Mono<Identity> checkTicket(ProcessorAccess access, Identity ticketId) {
        if (ticketId == null || ticketId.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.ticketService.getEntityName());

        return this.ticketService.checkAndUpdateIdentityWithAccess(access, ticketId);
    }

    protected Mono<Identity> checkOwner(ProcessorAccess access, Identity ownerId, Identity ticketId) {

        if (ownerId == null || ownerId.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.ownerService.getEntityName());

        if (ticketId == null || ticketId.isNull())
            return this.ownerService.checkAndUpdateIdentityWithAccess(access, ownerId);

        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.ownerService.readIdentityWithAccess(access, ownerId),
                        this.ticketService.readIdentityWithAccess(access, ticketId)),
                tOEntity -> {
                    if (!tOEntity.getT2().getOwnerId().equals(tOEntity.getT1().getId()))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.INVALID_TICKET_OWNER,
                                tOEntity.getT1().getName(),
                                tOEntity.getT1().getId());

                    return Mono.just(Identity.of(
                            tOEntity.getT1().getId().toBigInteger(),
                            tOEntity.getT1().getCode()));
                });
    }

    protected Mono<D> createTicketContent(ProcessorAccess access, D content) {
        if (content.getTicketId() == null || content.isOwnerContent()) return Mono.empty();

        return FlatMapUtil.flatMapMono(() -> this.ticketService.readById(content.getTicketId()), ticket -> {
                    content.setTicketId(ticket.getId());
                    content.setOwnerId(ticket.getOwnerId());
                    return this.createInternal(access, content);
                })
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.ticketService.getEntityName(),
                        content.getTicketId()));
    }

    protected Mono<D> createOwnerContent(ProcessorAccess access, D content) {
        if (content.getOwnerId() == null || content.isTicketContent()) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                        () -> this.ownerService.readById(content.getOwnerId()),
                        owner -> this.createInternal(access, content.setOwnerId(owner.getId())))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.ownerService.getEntityName(),
                        content.getTicketId()));
    }
}
