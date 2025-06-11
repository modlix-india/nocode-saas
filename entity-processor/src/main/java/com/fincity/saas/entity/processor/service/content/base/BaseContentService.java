package com.fincity.saas.entity.processor.service.content.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.model.request.content.BaseContentRequest;
import com.fincity.saas.entity.processor.service.OwnerService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

public abstract class BaseContentService<
                Q extends BaseContentRequest<Q>,
                R extends UpdatableRecord<R>,
                D extends BaseContentDto<Q, D>,
                O extends BaseContentDAO<Q, R, D>>
        extends BaseService<R, D, O> {

    private TicketService ticketService;

    private OwnerService ownerService;

    protected abstract Mono<D> createContent(Q contentRequest);

    @Autowired
    protected void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Autowired
    protected void setOwnerService(OwnerService ownerService) {
        this.ownerService = ownerService;
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

    protected Mono<Q> updateIdentities(Q contentRequest) {

        if (contentRequest.getTicketId() == null && contentRequest.getOwnerId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    this.ticketService.getEntityName());

        return FlatMapUtil.flatMapMono(
                () -> {
                    if (contentRequest.getTicketId() != null)
                        return this.ticketService
                                .checkAndUpdateIdentity(contentRequest.getTicketId())
                                .map(contentRequest::setTicketId);

                    return Mono.just(contentRequest);
                },
                tRequest -> {
                    if (tRequest.getOwnerId() != null)
                        return this.ownerService
                                .checkAndUpdateIdentity(tRequest.getOwnerId())
                                .map(tRequest::setOwnerId);

                    return Mono.just(tRequest);
                });
    }

    public Mono<D> create(Q contentRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.updateIdentities(contentRequest),
                (hasAccess, uRequest) -> this.createContent(uRequest),
                (hasAccess, uRequest, content) ->
                        content.isTicketContent() ? this.createTicketContent(content) : createOwnerContent(content));
    }

    private Mono<D> createTicketContent(D content) {
        if (content.getTicketId() == null || content.isOwnerContent()) return Mono.empty();

        return this.ticketService
                .readById(content.getTicketId())
                .flatMap(ticket -> {
                    content.setTicketId(ticket.getId());
                    content.setOwnerId(ticket.getOwnerId());
                    return this.create(content);
                })
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.ticketService.getEntityName(),
                        content.getTicketId()));
    }

    private Mono<D> createOwnerContent(D content) {
        if (content.getOwnerId() == null || content.isTicketContent()) return Mono.empty();

        return this.ownerService
                .readById(content.getOwnerId())
                .flatMap(owner -> this.create(content.setOwnerId(owner.getId())))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.IDENTITY_WRONG,
                        this.ownerService.getEntityName(),
                        content.getTicketId()));
    }
}
