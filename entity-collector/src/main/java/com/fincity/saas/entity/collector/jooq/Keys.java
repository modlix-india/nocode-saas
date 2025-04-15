/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.collector.jooq;


import com.fincity.saas.entity.collector.jooq.tables.CollectionLogs;
import com.fincity.saas.entity.collector.jooq.tables.EntityIntegrations;
import com.fincity.saas.entity.collector.jooq.tables.records.CollectionLogsRecord;
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

    public static final UniqueKey<CollectionLogsRecord> KEY_COLLECTION_LOGS_PRIMARY = Internal.createUniqueKey(CollectionLogs.COLLECTION_LOGS, DSL.name("KEY_collection_logs_PRIMARY"), new TableField[] { CollectionLogs.COLLECTION_LOGS.ID }, true);
    public static final UniqueKey<EntityIntegrationsRecord> KEY_ENTITY_INTEGRATIONS_PRIMARY = Internal.createUniqueKey(EntityIntegrations.ENTITY_INTEGRATIONS, DSL.name("KEY_entity_integrations_PRIMARY"), new TableField[] { EntityIntegrations.ENTITY_INTEGRATIONS.ID }, true);

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    public static final ForeignKey<CollectionLogsRecord, EntityIntegrationsRecord> FK1_COLLECTION_ENTITY_INTEGRATION_ID = Internal.createForeignKey(CollectionLogs.COLLECTION_LOGS, DSL.name("FK1_COLLECTION_ENTITY_INTEGRATION_ID"), new TableField[] { CollectionLogs.COLLECTION_LOGS.ENTITY_INTEGRATION_ID }, Keys.KEY_ENTITY_INTEGRATIONS_PRIMARY, new TableField[] { EntityIntegrations.ENTITY_INTEGRATIONS.ID }, true);
}
