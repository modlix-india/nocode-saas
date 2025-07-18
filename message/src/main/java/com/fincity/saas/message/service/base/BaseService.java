package com.fincity.saas.message.service.base;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.dao.base.BaseDAO;
import com.fincity.saas.message.dto.base.BaseDto;
import com.fincity.saas.message.enums.IMessageSeries;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.service.MessageResourceService;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

public abstract class BaseService<R extends UpdatableRecord<R>, D extends BaseDto<D>, O extends BaseDAO<R, D>>
        extends AbstractJOOQDataService<R, ULong, D, O> implements IMessageSeries, IMessageAccessService {

    @Getter
    protected MessageResourceService msgService;

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected CacheService cacheService;

    @Autowired
    public void setMessageResourceService(MessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    public void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Mono<D> createInternal(MessageAccess access, D entity) {

        entity.setAppCode(access.getAppCode());
        entity.setClientCode(access.getClientCode());

        entity.setCreatedBy(access.getUserId());

        return super.create(entity);
    }

    public AbstractCondition addAppCodeAndClientCodeToCondition(MessageAccess access, AbstractCondition condition) {
        if (condition == null || condition.isEmpty())
            return ComplexCondition.and(
                    FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, access.getAppCode())
                            .setOperator(FilterConditionOperator.EQUALS),
                    FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, access.getClientCode())
                            .setOperator(FilterConditionOperator.EQUALS));

        return ComplexCondition.and(
                condition,
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, access.getAppCode())
                        .setOperator(FilterConditionOperator.EQUALS),
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, access.getClientCode())
                        .setOperator(FilterConditionOperator.EQUALS));
    }
}
