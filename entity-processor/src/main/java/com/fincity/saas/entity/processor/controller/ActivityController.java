package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseController;
import com.fincity.saas.entity.processor.dao.ActivityDAO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import com.fincity.saas.entity.processor.service.ActivityService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/tickets/activities")
public class ActivityController
        extends BaseController<EntityProcessorActivitiesRecord, Activity, ActivityDAO, ActivityService> {}
