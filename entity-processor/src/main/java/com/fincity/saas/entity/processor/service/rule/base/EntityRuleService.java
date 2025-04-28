package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProductDto;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.jooq.UpdatableRecord;
import org.springframework.stereotype.Service;

@Service
public abstract class EntityRuleService<
                R extends UpdatableRecord<R>, D extends BaseProductDto<D>, O extends BaseProductDAO<R, D>>
        extends BaseService<R, D, O> {}
