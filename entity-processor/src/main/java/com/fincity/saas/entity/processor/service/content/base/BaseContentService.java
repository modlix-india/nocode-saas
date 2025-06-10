package com.fincity.saas.entity.processor.service.content.base;

import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.service.OwnerService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.base.BaseService;

import reactor.core.publisher.Mono;

public abstract class BaseContentService<
                R extends UpdatableRecord<R>, D extends BaseContentDto<D>, O extends BaseContentDAO<R, D>>
        extends BaseService<R, D, O> {

    private TicketService ticketService;

    private OwnerService ownerService;

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
            existing.setContent(entity.getContent());
            existing.setHasAttachment(entity.getHasAttachment());

			if (entity.getOwnerId() != null)
				existing.setOwnerId(entity.getOwnerId());

			if (entity.getTicketId() != null)
				existing.setTicketId(entity.getTicketId());

            return Mono.just(existing);
        });
    }

}
