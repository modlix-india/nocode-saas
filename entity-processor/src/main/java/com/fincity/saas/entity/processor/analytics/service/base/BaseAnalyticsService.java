package com.fincity.saas.entity.processor.analytics.service.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.ProductService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseAnalyticsService<
                R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>, O extends AbstractDAO<R, ULong, D>>
        extends AbstractJOOQDataService<R, ULong, D, O> implements IProcessorAccessService {

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected ProcessorMessageResourceService msgService;

    protected StageService stageService;

    protected ProductService productService;

    @Autowired
    private void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    private void setMsgService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }
}
