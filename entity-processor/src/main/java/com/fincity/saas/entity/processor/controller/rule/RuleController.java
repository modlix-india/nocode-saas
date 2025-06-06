package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.model.response.rule.RuleResponse;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public abstract class RuleController<
                R extends UpdatableRecord<R>,
                D extends Rule<D>,
                O extends RuleDAO<R, D>,
                S extends RuleService<R, D, O>>
        extends BaseController<R, D, O, S> {

    private static final String ORDER_PATH = REQ_PATH + "/order";
    private static final String ORDER_PATH_ID = REQ_PATH_ID + "/order";
    protected S ruleService;

    @Autowired
    private void setRuleConfigService(S ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping(ORDER_PATH_ID)
    public Mono<ResponseEntity<Map<Integer, D>>> createWithOrder(
            @PathVariable(PATH_VARIABLE_ID) Identity entityId, @RequestBody Map<Integer, RuleRequest> ruleRequests) {
        return this.ruleService.createWithOrder(entityId, ruleRequests).map(ResponseEntity::ok);
    }

    @PutMapping(ORDER_PATH)
    public Mono<ResponseEntity<Map<Integer, D>>> updateOrder(@RequestBody Map<Integer, Identity> ruleRequests) {
        return this.ruleService.updateOrder(ruleRequests).map(ResponseEntity::ok);
    }

    @GetMapping(ORDER_PATH_ID)
    public Mono<ResponseEntity<Map<Integer, RuleResponse<D>>>> getRuleResponseWithOrder(
            @PathVariable(PATH_VARIABLE_ID) Identity entityId) {
        return this.service
                .getRuleResponseWithOrder(entityId, null)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }
}
