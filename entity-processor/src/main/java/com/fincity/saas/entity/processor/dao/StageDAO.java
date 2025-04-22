package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dao.base.BaseProductDAO;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorStagesRecord;

@Component
public class StageDAO extends BaseProductDAO<EntityProcessorStagesRecord, Stage> {

	protected StageDAO() {
		super(
				Stage.class,
				ENTITY_PROCESSOR_STAGES,
				ENTITY_PROCESSOR_STAGES.ID,
				ENTITY_PROCESSOR_STAGES.CODE,
				ENTITY_PROCESSOR_STAGES.PRODUCT_ID,
				ENTITY_PROCESSOR_STAGES.IS_PARENT);
	}
}

