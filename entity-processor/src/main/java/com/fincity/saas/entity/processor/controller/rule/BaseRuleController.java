package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public abstract class BaseRuleController<
                R extends UpdatableRecord<R>,
                U extends BaseUserDistributionDto<U>,
                D extends BaseRuleDto<U, D>,
                O extends BaseRuleDAO<R, U, D>,
                S extends BaseRuleService<R, U, D, O>>
        extends BaseUpdatableController<R, D, O, S> {

    private static final String DISTRIBUTION_PATH = REQ_PATH + "/distributions";

    private static final String DISTRIBUTION_PATH_ID = REQ_PATH_ID + "/distributions";

    @PostMapping(DISTRIBUTION_PATH)
    public Mono<ResponseEntity<D>> createWithDistribution(@RequestBody RuleRequest<U, D> ruleRequest) {
        return this.service.createWithDistribution(ruleRequest).map(ResponseEntity::ok);
    }

    @PutMapping(DISTRIBUTION_PATH_ID)
    public Mono<ResponseEntity<D>> updateWithDistribution(
            @PathVariable(PATH_VARIABLE_ID) Identity identity, @RequestBody RuleRequest<U, D> ruleRequest) {
        return this.service.updateWithDistribution(identity, ruleRequest).map(ResponseEntity::ok);
    }
}
