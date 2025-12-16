package com.fincity.saas.entity.processor.service.content.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.BaseContentRequest;
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
import reactor.util.context.Context;

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
    protected boolean canOutsideCreate() {
        return Boolean.TRUE;
    }

    @Override
    @IgnoreGeneration
    public Mono<D> create(ProcessorAccess access, D entity) {
        return super.create(access, entity)
                .flatMap(cContent ->
                        this.activityService.acContentCreate(access, cContent).then(Mono.just(cContent)));
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
                () -> this.readByIdentity(identity),
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
    @IgnoreGeneration
    public Mono<D> update(ProcessorAccess access, D entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.readById(access, entity.getId()).map(CloneUtil::cloneObject),
                oEntity -> super.update(access, entity),
                (oEntity, uEntity) -> activityService
                        .acContentUpdate(access, oEntity, uEntity)
                        .then(Mono.just(uEntity)));
    }

    protected <T extends BaseContentRequest<T>> Mono<T> updateBaseIdentities(ProcessorAccess access, T request) {

        if (request.getUserId() != null) return Mono.just(request);

        Mono<Identity> ticketIdMono = request.getTicketId() != null
                ? this.checkTicket(access, request.getTicketId())
                : Mono.just(Identity.ofNull());

        Mono<Identity> ownerIdMono = request.getOwnerId() != null
                ? this.checkOwner(access, request.getOwnerId(), request.getTicketId())
                : Mono.just(Identity.ofNull());

        return Mono.zip(ticketIdMono, ownerIdMono)
                .map(tuple3 -> request.setTicketId(tuple3.getT1()).setOwnerId(tuple3.getT2()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseContentService.updateIdentities"));
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
                        this.ownerService.readByIdentity(access, ownerId),
                        this.ticketService.readByIdentity(access, ticketId)),
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

    protected Mono<D> createContent(ProcessorAccess access, D content) {
        return switch (content.getContentEntitySeries()) {
            case OWNER -> this.createOwnerContent(access, content);
            case TICKET -> this.createTicketContent(access, content);
            case USER -> this.createUserContent(access, content);
        };
    }

    private Mono<D> createTicketContent(ProcessorAccess access, D content) {
        if (content.getTicketId() == null || !content.getContentEntitySeries().equals(ContentEntitySeries.TICKET))
            return Mono.empty();

        return FlatMapUtil.flatMapMono(() -> this.ticketService.readById(access, content.getTicketId()), ticket -> {
                    content.setTicketId(ticket.getId());
                    content.setOwnerId(ticket.getOwnerId());
                    return this.create(access, content);
                })
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.ticketService.getEntityName(),
                        content.getTicketId()));
    }

    private Mono<D> createOwnerContent(ProcessorAccess access, D content) {
        if (content.getOwnerId() == null || !content.getContentEntitySeries().equals(ContentEntitySeries.OWNER))
            return Mono.empty();

        return FlatMapUtil.flatMapMono(
                        () -> this.ownerService.readById(access, content.getOwnerId()),
                        owner -> this.create(access, content.setOwnerId(owner.getId())))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.ownerService.getEntityName(),
                        content.getTicketId()));
    }

    private Mono<D> createUserContent(ProcessorAccess access, D content) {
        if (content.getUserId() == null || !content.getContentEntitySeries().equals(ContentEntitySeries.USER))
            return Mono.empty();

        return FlatMapUtil.flatMapMono(
                        () -> this.securityService.getUserInternal(
                                content.getUserId().toBigInteger(), null),
                        user -> {
                            content.setUserId(ULongUtil.valueOf(user.getId()));
                            content.setClientId(ULongUtil.valueOf(user.getClientId()));
                            return this.create(access, content);
                        })
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        "user",
                        content.getUserId()));
    }
}
