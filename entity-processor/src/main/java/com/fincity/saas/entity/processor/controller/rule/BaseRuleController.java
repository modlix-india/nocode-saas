package com.fincity.saas.entity.processor.controller.rule;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.rule.BaseRuleService;
import java.util.List;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

public abstract class BaseRuleController<
                R extends UpdatableRecord<R>,
                D extends BaseRuleDto<D>,
                O extends BaseRuleDAO<R, D>,
                S extends BaseRuleService<R, D, O>>
        extends BaseUpdatableController<R, D, O, S> {

    private static final String ORDER_PATH_ID = REQ_PATH_ID + "/order";

    @PostMapping(ORDER_PATH_ID)
    public Mono<ResponseEntity<Map<Integer, D>>> createWithOrder(
            @PathVariable(PATH_VARIABLE_ID) Identity entityId, @RequestBody Map<Integer, D> ruleRequests) {
        return this.service.createWithOrder(entityId, ruleRequests).map(ResponseEntity::ok);
    }

    @PutMapping(ORDER_PATH_ID)
    public Mono<ResponseEntity<Map<Integer, D>>> updateOrder(
            @PathVariable(PATH_VARIABLE_ID) Identity entityId, @RequestBody Map<Integer, Identity> ruleRequests) {
        return this.service.updateOrder(entityId, ruleRequests).map(ResponseEntity::ok);
    }

    @GetMapping(ORDER_PATH_ID)
    public Mono<ResponseEntity<Map<Integer, D>>> getRulesWithOrder(
            @PathVariable(PATH_VARIABLE_ID) Identity entityId,
            @RequestParam(required = false) List<ULong> stageIds,
            @RequestParam(required = false) Boolean includeDefault) {
        return this.service
                .getRulesWithOrder(entityId, stageIds, includeDefault)
                .map(ResponseEntity::ok)
                .switchIfEmpty(
                        Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }
}
