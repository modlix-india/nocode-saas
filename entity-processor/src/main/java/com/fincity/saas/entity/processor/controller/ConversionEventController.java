package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ConversionEventDAO;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionEventsRecord;
import com.fincity.saas.entity.processor.service.ConversionEventService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side outbox accessor. Standard CRUD + {@code /eager/query} come from
 * {@link BaseUpdatableController} -- UI hits {@code POST /eager/query} with a
 * {@code Query} body to list rows ordered by id desc (or any column), filtered
 * by status / ticket / mapping / since.
 *
 * <p>The drain worker uses {@link ConversionEventService#findDispatchable} and
 * does NOT route through this controller; this surface exists purely so the
 * operator UI can see what landed in the outbox and what failed.
 */
@RestController
@RequestMapping("api/entity/processor/conversion/events")
public class ConversionEventController
        extends BaseUpdatableController<
                EntityProcessorConversionEventsRecord,
                ConversionEvent,
                ConversionEventDAO,
                ConversionEventService> {}
