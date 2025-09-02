package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.ActivityDAO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.ActivityService;
import com.fincity.saas.entity.processor.util.EagerUtil;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@RestController
@RequestMapping("api/entity/processor/activities")
public class ActivityController
        extends BaseController<EntityProcessorActivitiesRecord, Activity, ActivityDAO, ActivityService> {

    public static final String TICKET_PATH = "/tickets";
    public static final String TICKET_PATH_ID = TICKET_PATH + PATH_ID;
    public static final String EAGER_TICKET_PATH_ID = TICKET_PATH_ID + EAGER_BASE;

    @GetMapping(TICKET_PATH_ID)
    public Mono<ResponseEntity<Page<Activity>>> readPageFilter(
            Pageable pageable, @PathVariable(PATH_VARIABLE_ID) final Identity identity, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.ASC, PATH_VARIABLE_ID) : pageable);
        return this.service
                .readPageFilter(pageable, identity, ConditionUtil.parameterMapToMap(request.getQueryParams()))
                .map(ResponseEntity::ok);
    }

    @GetMapping(EAGER_TICKET_PATH_ID)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            Pageable pageable, @PathVariable(PATH_VARIABLE_ID) final Identity identity, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Sort.Direction.DESC, PATH_VARIABLE_ID) : pageable);

        Tuple2<AbstractCondition, List<String>> fieldParams = EagerUtil.getFieldConditions(request.getQueryParams());

        return this.service
                .readPageFilterEager(
                        pageable, identity, fieldParams.getT1(), fieldParams.getT2(), request.getQueryParams())
                .map(ResponseEntity::ok);
    }
}
