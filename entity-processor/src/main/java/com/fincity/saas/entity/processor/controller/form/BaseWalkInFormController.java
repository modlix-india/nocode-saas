package com.fincity.saas.entity.processor.controller.form;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.form.BaseWalkInFromDAO;
import com.fincity.saas.entity.processor.dto.form.BaseWalkInFormDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.form.WalkInFormRequest;
import com.fincity.saas.entity.processor.service.form.BaseWalkInFormService;
import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public abstract class BaseWalkInFormController<
                R extends UpdatableRecord<R>,
                D extends BaseWalkInFormDto<D>,
                O extends BaseWalkInFromDAO<R, D>,
                S extends BaseWalkInFormService<R, D, O>>
        extends BaseUpdatableController<R, D, O, S> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<D>> createFromRequest(@RequestBody WalkInFormRequest walkInFormRequest) {
        return this.service.create(walkInFormRequest).map(ResponseEntity::ok);
    }

    @GetMapping(REQ_PATH_ID + "/forms")
    public Mono<ResponseEntity<D>> getWalkInForm(@PathVariable(PATH_VARIABLE_ID) final Identity productId) {
        return this.service.getWalkInForm(productId).map(ResponseEntity::ok);
    }
}
