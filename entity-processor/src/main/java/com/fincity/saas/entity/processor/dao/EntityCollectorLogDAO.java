package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCollectorLog.ENTITY_PROCESSOR_COLLECTOR_LOG;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.entity.processor.dto.EntityCollectorLog;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCollectorLogRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

@Component
public class EntityCollectorLogDAO
        extends AbstractUpdatableDAO<EntityProcessorCollectorLogRecord, ULong, EntityCollectorLog> {

    protected EntityCollectorLogDAO() {
        super(EntityCollectorLog.class, ENTITY_PROCESSOR_COLLECTOR_LOG, ENTITY_PROCESSOR_COLLECTOR_LOG.ID);
    }
}
