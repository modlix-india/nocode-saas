package com.fincity.saas.entity.processor.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.util.EagerUtil;
import java.util.List;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

public abstract class BaseController<
                R extends UpdatableRecord<R>,
                D extends BaseDto<D>,
                O extends BaseDAO<R, D>,
                S extends BaseService<R, D, O>>
        extends AbstractJOOQDataController<R, ULong, D, O, S> {

    public static final String EAGER_BASE = "/eager";
    public static final String EAGER_PATH_QUERY = EAGER_BASE + "/query";

    @GetMapping(EAGER_BASE)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.DESC, PATH_VARIABLE_ID) : pageable);

        Tuple4<AbstractCondition, List<String>, Boolean, List<String>> eagerParams =
                EagerUtil.getEagerConditions(request.getQueryParams());
        return this.service
                .readPageFilterEager(
                        pageable, eagerParams.getT1(), eagerParams.getT2(), eagerParams.getT3(), eagerParams.getT4())
                .map(ResponseEntity::ok);
    }

    @PostMapping(EAGER_PATH_QUERY)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(@RequestBody Query query) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return this.service
                .readPageFilterEager(
                        pageable, query.getCondition(), query.getFields(), query.getEager(), query.getEagerFields())
                .map(ResponseEntity::ok);
    }
}
