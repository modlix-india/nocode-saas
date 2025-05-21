package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

public abstract class RuleController<
                R extends UpdatableRecord<R>,
                D extends Rule<D>,
                O extends RuleDAO<R, D>,
                S extends RuleService<R, D, O>>
        extends BaseController<R, D, O, S> {

    protected S ruleConfigService;

    @Autowired
    private void setRuleConfigService(S ruleConfigService) {
        this.ruleConfigService = ruleConfigService;
    }

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<D>> createFromRequest(@RequestBody T ruleConfigRequest) {
        return this.ruleConfigService.create(ruleConfigRequest).map(ResponseEntity::ok);
    }

    @PutMapping(REQ_PATH_ID)
    public Mono<ResponseEntity<D>> put(
            @PathVariable(name = PATH_VARIABLE_ID, required = false) final Identity identity,
            @RequestBody T ruleConfigRequest) {

        if (!identity.isNull()) ruleConfigRequest.setRuleConfigId(identity);

        return this.ruleConfigService.update(ruleConfigRequest).map(ResponseEntity::ok);
    }

    @DeleteMapping(REQ_PATH_ID)
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    public Mono<Integer> deleteFromRequest(@PathVariable(PATH_VARIABLE_ID) final Identity identity) {
        return this.ruleConfigService.deleteIdentity(identity);
    }
}
