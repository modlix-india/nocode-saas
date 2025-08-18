package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseAnalyticsService<
                R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>, O extends AbstractDAO<R, ULong, D>>
        extends AbstractJOOQDataService<R, ULong, D, O> implements IProcessorAccessService {

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected ProcessorMessageResourceService msgService;
}
