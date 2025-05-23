package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

public abstract class RuleController<
                R extends UpdatableRecord<R>,
                D extends Rule<D>,
                O extends RuleDAO<R, D>,
                S extends RuleService<R, D, O>>
        extends BaseController<R, D, O, S> {

    protected S ruleService;

    @Autowired
    private void setRuleConfigService(S ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<Map<Integer, D>>> createFromRequest(
            @RequestBody Map<Integer, RuleRequest> ruleRequests) {
        return this.ruleService.createWithOrder(ruleRequests).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteFromRequest(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.ruleService.deleteIdentity(identity);
    }
}
