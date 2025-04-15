package com.fincity.saas.entity.collector.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.entity.collector.dto.CollectionLog;
import com.fincity.saas.entity.collector.jooq.tables.records.CollectionLogsRecord;
import org.jooq.types.ULong;


import static com.fincity.saas.entity.collector.jooq.tables.CollectionLogs.COLLECTION_LOGS;

public class CollectionLogDAO extends AbstractUpdatableDAO<CollectionLogsRecord, ULong, CollectionLog> {

    protected CollectionLogDAO()  {
        super(CollectionLog.class, COLLECTION_LOGS, COLLECTION_LOGS.ID);
    }
}
