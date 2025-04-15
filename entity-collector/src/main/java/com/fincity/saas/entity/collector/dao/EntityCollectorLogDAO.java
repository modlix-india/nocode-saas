package com.fincity.saas.entity.collector.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.entity.collector.dto.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Repository;

import static com.fincity.saas.entity.collector.jooq.tables.EntityCollectorLog.ENTITY_COLLECTOR_LOG;

@Repository
public class EntityCollectorLogDAO extends AbstractUpdatableDAO<EntityCollectorLogRecord, ULong, EntityCollectorLog> {

    protected EntityCollectorLogDAO()  {
        super(EntityCollectorLog.class, ENTITY_COLLECTOR_LOG, ENTITY_COLLECTOR_LOG.ID);
    }
}
