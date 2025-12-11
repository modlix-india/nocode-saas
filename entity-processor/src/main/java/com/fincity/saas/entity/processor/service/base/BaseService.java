package com.fincity.saas.entity.processor.service.base;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.jooq.flow.service.AbstractFlowDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public abstract class BaseService<R extends UpdatableRecord<R>, D extends BaseDto<D>, O extends BaseDAO<R, D>>
        extends AbstractFlowDataService<R, ULong, D, O> implements IEntitySeries, IProcessorAccessService {

    @Getter
    protected IFeignSecurityService securityService;

    @Getter
    protected ProcessorMessageResourceService msgService;

    @Getter
    protected CacheService cacheService;

    @Autowired
    protected void setMessageResourceService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    protected void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    protected void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    protected Mono<D> createInternal(ProcessorAccess access, D entity) {

        if (entity.getName() == null || entity.getName().isEmpty()) entity.setName(entity.getCode());

        entity.setAppCode(access.getAppCode());
        entity.setClientCode(access.getClientCode());

        entity.setCreatedBy(access.getUserId());

        return super.create(entity);
    }

    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {
        return this.hasAccess()
                .flatMap(access -> this.dao.readPageFilterEager(
                        pageable,
                        this.addAppCodeAndClientCodeToCondition(access, condition),
                        tableFields,
                        queryParams));
    }

    protected AbstractCondition addAppCodeAndClientCodeToCondition(
            ProcessorAccess access, AbstractCondition condition) {
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
