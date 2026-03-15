package com.fincity.saas.entity.processor.service;

import com.fincity.saas.commons.jooq.flow.service.AbstractFlowDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.dao.DiagnosticsDAO;
import com.fincity.saas.entity.processor.dto.DiagnosticsLog;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorDiagnosticsObjectType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorDiagnosticsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.common.RuleResult;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DiagnosticsService
        extends AbstractFlowDataService<EntityProcessorDiagnosticsRecord, ULong, DiagnosticsLog, DiagnosticsDAO>
        implements IProcessorAccessService {

    @Getter
    private IFeignSecurityService securityService;

    @Getter
    private ProcessorMessageResourceService msgService;

    @Autowired
    public void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setMsgService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return Mono.empty();
    }

    public Mono<DiagnosticsLog> log(
            ProcessorAccess access,
            EntityProcessorDiagnosticsObjectType objectType,
            ULong objectId,
            String action,
            ULong oldValue,
            ULong newValue,
            String reason,
            Map<String, Object> metaData) {

        DiagnosticsLog entry = (DiagnosticsLog) new DiagnosticsLog()
                .setObjectType(objectType)
                .setObjectId(objectId)
                .setAction(action)
                .setOldValue(oldValue != null ? oldValue.toString() : null)
                .setNewValue(newValue != null ? newValue.toString() : null)
                .setReason(reason)
                .setActorId(access.getUserId())
                .setMetaData(metaData)
                .setAppCode(access.getAppCode())
                .setClientCode(
                        access.isOutsideUser()
                                ? access.getUserInherit().getManagedClientCode()
                                : access.getClientCode());

        entry.setCreatedBy(access.getUserId());

        return this.create(entry);
    }

    public Mono<DiagnosticsLog> logAssignment(
            ProcessorAccess access,
            ULong ticketId,
            String action,
            ULong oldUserId,
            ULong newUserId,
            String reason,
            RuleResult ruleResult) {

        Map<String, Object> metaData = new HashMap<>();
        if (ruleResult != null) {
            metaData.put("ruleId", ruleResult.getRuleId() != null ? ruleResult.getRuleId().toString() : null);
            metaData.put("ruleOrder", ruleResult.getRuleOrder());
            metaData.put(
                    "distributionType",
                    ruleResult.getDistributionType() != null
                            ? ruleResult.getDistributionType().getLiteral()
                            : null);
            metaData.put(
                    "productId", ruleResult.getProductId() != null ? ruleResult.getProductId().toString() : null);
            metaData.put(
                    "productTemplateId",
                    ruleResult.getProductTemplateId() != null
                            ? ruleResult.getProductTemplateId().toString()
                            : null);
            metaData.put("stageId", ruleResult.getStageId() != null ? ruleResult.getStageId().toString() : null);
        }

        return this.log(
                access,
                EntityProcessorDiagnosticsObjectType.TICKET,
                ticketId,
                action,
                oldUserId,
                newUserId,
                reason,
                metaData);
    }

    public Mono<Page<DiagnosticsLog>> readPageFiltered(ProcessorAccess access, Pageable pageable,
            AbstractCondition condition) {

        AbstractCondition filtered = addAppCodeAndClientCodeToCondition(access, condition);
        return this.readPageFilter(pageable, filtered);
    }

    private AbstractCondition addAppCodeAndClientCodeToCondition(
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
