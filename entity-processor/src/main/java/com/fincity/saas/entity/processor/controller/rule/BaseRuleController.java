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

}
