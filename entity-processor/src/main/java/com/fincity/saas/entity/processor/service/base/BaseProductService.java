package com.fincity.saas.entity.processor.service.base;

import org.jooq.UpdatableRecord;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProductDto;

import reactor.core.publisher.Mono;

@Service
public abstract class BaseProductService<
                R extends UpdatableRecord<R>, D extends BaseProductDto<D>, O extends BaseProductDAO<R, D>>
        extends BaseService<R, D, O> {

	@Override
	protected Mono<D> updatableEntity(D entity) {
		return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {

			e.setParentLevel0(entity.getParentLevel0());
			e.setParentLevel1(entity.getParentLevel1());

			return Mono.just(e);
		});
	}


}
