package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorActivitiesRecord;
import org.springframework.stereotype.Component;

@Component
public class ActivityDAO extends BaseDAO<EntityProcessorActivitiesRecord, Activity> {

    protected ActivityDAO() {
        super(Activity.class, ENTITY_PROCESSOR_ACTIVITIES, ENTITY_PROCESSOR_ACTIVITIES.ID);
    }
}
