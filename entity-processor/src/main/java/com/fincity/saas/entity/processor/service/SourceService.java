package com.fincity.saas.entity.processor.service;

import org.springframework.stereotype.Service;

import com.fincity.saas.entity.processor.dao.SourceDAO;
import com.fincity.saas.entity.processor.dto.Source;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSourcesRecord;
import com.fincity.saas.entity.processor.service.base.BaseProductService;

@Service
public class SourceService extends BaseProductService<EntityProcessorSourcesRecord, Source, SourceDAO> {

	private static final String SOURCE_CACHE = "source";

	@Override
	protected String getCacheName() {
		return SOURCE_CACHE;
	}
}

