/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.collector.jooq.tables;


import com.fincity.saas.entity.collector.jooq.EntityCollector;
import com.fincity.saas.entity.collector.jooq.Keys;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.tables.EntityIntegrations.EntityIntegrationsPath;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.InverseForeignKey;
import org.jooq.JSON;
import org.jooq.Name;
import org.jooq.Path;
import org.jooq.PlainSQL;
import org.jooq.QueryPart;
import org.jooq.Record;
import org.jooq.SQL;
import org.jooq.Schema;
import org.jooq.Select;
import org.jooq.Stringly;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class EntityCollectorLog extends TableImpl<EntityCollectorLogRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>entity_collector.entity_collector_log</code>
     */
    public static final EntityCollectorLog ENTITY_COLLECTOR_LOG = new EntityCollectorLog();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<EntityCollectorLogRecord> getRecordType() {
        return EntityCollectorLogRecord.class;
    }

    /**
     * The column <code>entity_collector.entity_collector_log.id</code>. Primary
     * key
     */
    public final TableField<EntityCollectorLogRecord, ULong> ID = createField(DSL.name("id"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>entity_collector.entity_collector_log.entity_integration_id</code>.
     * Entity integration ID
     */
    public final TableField<EntityCollectorLogRecord, ULong> ENTITY_INTEGRATION_ID = createField(DSL.name("entity_integration_id"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Entity integration ID");

    /**
     * The column
     * <code>entity_collector.entity_collector_log.incoming_lead_data</code>.
     * Lead Data
     */
    public final TableField<EntityCollectorLogRecord, JSON> INCOMING_LEAD_DATA = createField(DSL.name("incoming_lead_data"), SQLDataType.JSON.nullable(false), this, "Lead Data");

    /**
     * The column <code>entity_collector.entity_collector_log.ip_address</code>.
     * Ip Address
     */
    public final TableField<EntityCollectorLogRecord, String> IP_ADDRESS = createField(DSL.name("ip_address"), SQLDataType.VARCHAR(320), this, "Ip Address");

    /**
     * The column
     * <code>entity_collector.entity_collector_log.outgoing_lead_data</code>.
     * Lead Data Forwarded to CRM
     */
    public final TableField<EntityCollectorLogRecord, JSON> OUTGOING_LEAD_DATA = createField(DSL.name("outgoing_lead_data"), SQLDataType.JSON.nullable(false), this, "Lead Data Forwarded to CRM");

    /**
     * The column <code>entity_collector.entity_collector_log.status</code>.
     * Status of the Lead Transfer
     */
    public final TableField<EntityCollectorLogRecord, EntityCollectorLogStatus> STATUS = createField(DSL.name("status"), SQLDataType.VARCHAR(11).nullable(false).asEnumDataType(EntityCollectorLogStatus.class), this, "Status of the Lead Transfer");

    /**
     * The column
     * <code>entity_collector.entity_collector_log.status_message</code>.
     * Message given for the status
     */
    public final TableField<EntityCollectorLogRecord, String> STATUS_MESSAGE = createField(DSL.name("status_message"), SQLDataType.CLOB, this, "Message given for the status");

    /**
     * The column <code>entity_collector.entity_collector_log.created_at</code>.
     * Time when this row is created
     */
    public final TableField<EntityCollectorLogRecord, LocalDateTime> CREATED_AT = createField(DSL.name("created_at"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    private EntityCollectorLog(Name alias, Table<EntityCollectorLogRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private EntityCollectorLog(Name alias, Table<EntityCollectorLogRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>entity_collector.entity_collector_log</code>
     * table reference
     */
    public EntityCollectorLog(String alias) {
        this(DSL.name(alias), ENTITY_COLLECTOR_LOG);
    }

    /**
     * Create an aliased <code>entity_collector.entity_collector_log</code>
     * table reference
     */
    public EntityCollectorLog(Name alias) {
        this(alias, ENTITY_COLLECTOR_LOG);
    }

    /**
     * Create a <code>entity_collector.entity_collector_log</code> table
     * reference
     */
    public EntityCollectorLog() {
        this(DSL.name("entity_collector_log"), null);
    }

    public <O extends Record> EntityCollectorLog(Table<O> path, ForeignKey<O, EntityCollectorLogRecord> childPath, InverseForeignKey<O, EntityCollectorLogRecord> parentPath) {
        super(path, childPath, parentPath, ENTITY_COLLECTOR_LOG);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class EntityCollectorLogPath extends EntityCollectorLog implements Path<EntityCollectorLogRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> EntityCollectorLogPath(Table<O> path, ForeignKey<O, EntityCollectorLogRecord> childPath, InverseForeignKey<O, EntityCollectorLogRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private EntityCollectorLogPath(Name alias, Table<EntityCollectorLogRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public EntityCollectorLogPath as(String alias) {
            return new EntityCollectorLogPath(DSL.name(alias), this);
        }

        @Override
        public EntityCollectorLogPath as(Name alias) {
            return new EntityCollectorLogPath(alias, this);
        }

        @Override
        public EntityCollectorLogPath as(Table<?> alias) {
            return new EntityCollectorLogPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : EntityCollector.ENTITY_COLLECTOR;
    }

    @Override
    public Identity<EntityCollectorLogRecord, ULong> getIdentity() {
        return (Identity<EntityCollectorLogRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<EntityCollectorLogRecord> getPrimaryKey() {
        return Keys.KEY_ENTITY_COLLECTOR_LOG_PRIMARY;
    }

    @Override
    public List<ForeignKey<EntityCollectorLogRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_COLLECTOR_ENTITY_INTEGRATION_ID);
    }

    private transient EntityIntegrationsPath _entityIntegrations;

    /**
     * Get the implicit join path to the
     * <code>entity_collector.entity_integrations</code> table.
     */
    public EntityIntegrationsPath entityIntegrations() {
        if (_entityIntegrations == null)
            _entityIntegrations = new EntityIntegrationsPath(this, Keys.FK1_COLLECTOR_ENTITY_INTEGRATION_ID, null);

        return _entityIntegrations;
    }

    @Override
    public EntityCollectorLog as(String alias) {
        return new EntityCollectorLog(DSL.name(alias), this);
    }

    @Override
    public EntityCollectorLog as(Name alias) {
        return new EntityCollectorLog(alias, this);
    }

    @Override
    public EntityCollectorLog as(Table<?> alias) {
        return new EntityCollectorLog(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityCollectorLog rename(String name) {
        return new EntityCollectorLog(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityCollectorLog rename(Name name) {
        return new EntityCollectorLog(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityCollectorLog rename(Table<?> name) {
        return new EntityCollectorLog(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityCollectorLog where(Condition condition) {
        return new EntityCollectorLog(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityCollectorLog where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityCollectorLog where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityCollectorLog where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityCollectorLog where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityCollectorLog where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityCollectorLog where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityCollectorLog where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityCollectorLog whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityCollectorLog whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
