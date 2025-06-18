package com.fincity.saas.entity.processor.service.base;

import com.fincity.saas.commons.jooq.flow.service.AbstractFlowDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

public abstract class BaseService<R extends UpdatableRecord<R>, D extends BaseDto<D>, O extends BaseDAO<R, D>>
        extends AbstractFlowDataService<R, ULong, D, O> implements IEntitySeries {

    protected IFeignSecurityService securityService;
    protected ProcessorMessageResourceService msgService;

    @Autowired
    public void setMessageResourceService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    public void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    public Mono<ProcessorAccess> hasAccess() {
        return SecurityContextUtil.resolveAppAndClientCode(null, null)
                .map(tup -> ProcessorAccess.of(tup.getT1(), tup.getT2(), false));
    }
}
