package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    protected abstract Mono<D> checkEntity(D entity, ProcessorAccess access);

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), existing -> {
            if (existing.getVersion() != entity.getVersion())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                        ProcessorMessageResourceService.VERSION_MISMATCH);

            existing.setVersion(existing.getVersion() + 1);
            return Mono.just(existing);
        });
    }

    @Override
    public Mono<D> create(D entity) {
        return super.hasAccess().flatMap(access -> this.createInternal(access, entity));
    }

    public Mono<D> createPublic(D entity) {
        return super.hasPublicAccess().flatMap(access -> this.createInternal(access, entity));
    }

    @Override
    public Mono<D> createInternal(ProcessorAccess access, D entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.checkEntity(entity, access),
                cEntity -> access.isOutsideUser()
                        ? Mono.just(cEntity.setClientId(access.getUser().getClientId()))
                        : Mono.just(cEntity),
                (cEntity, uEntity) -> super.createInternal(access, uEntity));
    }

    public Mono<D> readIdentityWithOwnerAccess(Identity identity) {
        return this.hasAccess().flatMap(access -> this.readIdentityWithOwnerAccess(access, identity));
    }

    public Mono<D> readIdentityWithOwnerAccess(ProcessorAccess access, Identity identity) {

        if (identity == null || identity.isNull()) return this.identityMissingError();

        return identity.isCode()
                ? this.readByCode(access, identity.getCode())
                        .flatMap(ticket -> this.checkUserAccess(access, ticket))
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.IDENTITY_WRONG,
                                this.getEntityName(),
                                identity.getCode()))
                : this.readById(access, identity.getULongId())
                        .flatMap(ticket -> this.checkUserAccess(access, ticket))
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.IDENTITY_WRONG,
                                this.getEntityName(),
                                identity.getId()));
    }

    public Mono<D> checkUserAccess(ProcessorAccess access, D entity) {
        ULong accessUser = entity.getAccessUser();

        return (accessUser != null && access.getUserInherit().getSubOrg().contains(accessUser)) ? Mono.just(entity) : Mono.empty();
    }

    @Override
    public Mono<D> update(ULong key, Map<String, Object> fields) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> key != null ? this.read(key) : Mono.empty(),
                (access, entity) -> super.update(key, fields),
                (access, entity, updated) ->
                        this.evictCache(entity).map(evicted -> updated).switchIfEmpty(Mono.just(updated)));
    }

    @Override
    public Mono<D> update(D entity) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.checkEntity(entity, access),
                (access, cEntity) -> this.updateInternal(cEntity));
    }

    public Mono<D> updateInternal(D entity) {
        return super.update(entity).flatMap(updated -> this.evictCache(entity).map(evicted -> updated));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.read(id),
                (access, entity) -> super.delete(entity.getId()),
                (ca, entity, deleted) -> this.evictCache(entity).map(evicted -> deleted));
    }
}
