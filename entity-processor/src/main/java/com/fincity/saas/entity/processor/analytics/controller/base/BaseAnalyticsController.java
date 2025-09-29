package com.fincity.saas.entity.processor.analytics.controller.base;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.service.base.BaseAnalyticsService;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseAnalyticsController<
                R extends UpdatableRecord<R>,
                D extends AbstractDTO<ULong, ULong>,
                O extends BaseAnalyticsDAO<R, D>,
                S extends BaseAnalyticsService<R, D, O>>
        extends AbstractJOOQDataController<R, ULong, D, O, S> {}
