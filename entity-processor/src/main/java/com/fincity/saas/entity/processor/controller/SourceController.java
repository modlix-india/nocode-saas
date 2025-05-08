package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseProductController;
import com.fincity.saas.entity.processor.dao.SourceDAO;
import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSourcesRecord;
import com.fincity.saas.entity.processor.service.SourceService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/entity/processor/sources")
public class SourceController
        extends BaseProductController<EntityProcessorSourcesRecord, Source, SourceDAO, SourceService> {}
