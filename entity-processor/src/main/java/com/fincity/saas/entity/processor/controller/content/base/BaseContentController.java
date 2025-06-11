package com.fincity.saas.entity.processor.controller.content.base;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.model.request.content.BaseContentRequest;
import com.fincity.saas.entity.processor.service.content.base.BaseContentService;
import org.jooq.UpdatableRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public abstract class BaseContentController<
                Q extends BaseContentRequest<Q>,
                R extends UpdatableRecord<R>,
                D extends BaseContentDto<Q, D>,
                O extends BaseContentDAO<Q, R, D>,
                S extends BaseContentService<Q, R, D, O>>
        extends BaseController<R, D, O, S> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<D>> createFromRequest(@RequestBody Q contentRequest) {
        return this.service.create(contentRequest).map(ResponseEntity::ok);
    }
}
