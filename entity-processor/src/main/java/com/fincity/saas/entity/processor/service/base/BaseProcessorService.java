package com.fincity.saas.entity.processor.service.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.List;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public abstract class BaseProcessorService<
                R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>, O extends BaseProcessorDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

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
    public Mono<D> create(ProcessorAccess access, D entity) {
        return FlatMapUtil.flatMapMono(
                () -> access.isOutsideUser()
                        ? Mono.just(entity.setClientId(
                                ULongUtil.valueOf(access.getUser().getClientId())))
                        : Mono.just(entity),
                uEntity -> super.create(access, uEntity));
    }

    /**
     * Raised as 409 CONFLICT rather than 400, matching how the rest of the platform already reports
     * "this already exists" - see {@code UserInviteService} (USER_ALREADY_EXISTS) and
     * {@code AppService} (APP_SUBDOMAIN_TAKEN) in the security service.
     *
     * <p>The distinct status is also what lets the provider-facing open intakes single this outcome
     * out: a duplicate lead has already had its re-inquiry committed, so it needs a 200
     * acknowledgement to stop Meta and Google redelivering it, while every other rejection on that
     * path must stay a visible error. The message body is unchanged.
     */
    protected <T> Mono<T> throwDuplicateError(ProcessorAccess access, D existing) {

        if (access.isOutsideUser())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.CONFLICT, msg),
                    ProcessorMessageResourceService.DUPLICATE_ENTITY_OUTSIDE_USER,
                    this.getEntityPrefix(access.getAppCode()));

        return this.msgService.throwMessage(
                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                ProcessorMessageResourceService.DUPLICATE_ENTITY,
                this.getEntityPrefix(access.getAppCode()),
                existing.getId(),
                this.getEntityPrefix(access.getAppCode()));
    }

    public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition, String timezone) {
        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.dao.processorAccessCondition(condition, access),
                (access, pCondition) -> this.dao.readPageFilter(pageable, pCondition, timezone));
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams) {
        return this.readPageFilterEager(pageable, condition, fields, timezone, queryParams, null);
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams,
            Map<String, AbstractCondition> subQueryConditions) {

        return FlatMapUtil.flatMapMono(
                this::hasAccess,
                access -> this.dao.processorAccessCondition(condition, access),
                (access, pCondition) -> this.dao.readPageFilterEager(
                        pageable, pCondition, fields, timezone, queryParams, subQueryConditions));
    }
}
