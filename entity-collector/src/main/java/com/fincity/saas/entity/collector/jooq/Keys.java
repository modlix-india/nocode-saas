/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.collector.jooq;


import com.fincity.saas.entity.collector.jooq.tables.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.tables.EntityIntegrations;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;

import org.jooq.ForeignKey;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * entity_collector.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<EntityCollectorLogRecord> KEY_ENTITY_COLLECTOR_LOG_PRIMARY = Internal.createUniqueKey(EntityCollectorLog.ENTITY_COLLECTOR_LOG, DSL.name("KEY_entity_collector_log_PRIMARY"), new TableField[] { EntityCollectorLog.ENTITY_COLLECTOR_LOG.ID }, true);
    public static final UniqueKey<EntityIntegrationsRecord> KEY_ENTITY_INTEGRATIONS_PRIMARY = Internal.createUniqueKey(EntityIntegrations.ENTITY_INTEGRATIONS, DSL.name("KEY_entity_integrations_PRIMARY"), new TableField[] { EntityIntegrations.ENTITY_INTEGRATIONS.ID }, true);

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    public static final ForeignKey<EntityCollectorLogRecord, EntityIntegrationsRecord> FK1_COLLECTOR_ENTITY_INTEGRATION_ID = Internal.createForeignKey(EntityCollectorLog.ENTITY_COLLECTOR_LOG, DSL.name("FK1_collector_entity_integration_id"), new TableField[] { EntityCollectorLog.ENTITY_COLLECTOR_LOG.ENTITY_INTEGRATION_ID }, Keys.KEY_ENTITY_INTEGRATIONS_PRIMARY, new TableField[] { EntityIntegrations.ENTITY_INTEGRATIONS.ID }, true);
}
