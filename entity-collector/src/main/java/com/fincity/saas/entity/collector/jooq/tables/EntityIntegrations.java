/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.collector.jooq.tables;


import com.fincity.saas.entity.collector.jooq.EntityCollector;
import com.fincity.saas.entity.collector.jooq.Keys;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import com.fincity.saas.entity.collector.jooq.tables.EntityCollectorLog.EntityCollectorLogPath;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityIntegrationsRecord;

import java.time.LocalDateTime;
import java.util.Collection;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.InverseForeignKey;
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
public class EntityIntegrations extends TableImpl<EntityIntegrationsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>entity_collector.entity_integrations</code>
     */
    public static final EntityIntegrations ENTITY_INTEGRATIONS = new EntityIntegrations();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<EntityIntegrationsRecord> getRecordType() {
        return EntityIntegrationsRecord.class;
    }

    /**
     * The column <code>entity_collector.entity_integrations.id</code>. Primary
     * key, unique identifier for each Entity Integration
     */
    public final TableField<EntityIntegrationsRecord, ULong> ID = createField(DSL.name("id"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key, unique identifier for each Entity Integration");

    /**
     * The column <code>entity_collector.entity_integrations.client_code</code>.
     * Client Code
     */
    public final TableField<EntityIntegrationsRecord, String> CLIENT_CODE = createField(DSL.name("client_code"), SQLDataType.CHAR(8).nullable(false), this, "Client Code");

    /**
     * The column <code>entity_collector.entity_integrations.app_code</code>.
     * App Code
     */
    public final TableField<EntityIntegrationsRecord, String> APP_CODE = createField(DSL.name("app_code"), SQLDataType.CHAR(8).nullable(false), this, "App Code");

    /**
     * The column <code>entity_collector.entity_integrations.target</code>.
     * Target
     */
    public final TableField<EntityIntegrationsRecord, String> TARGET = createField(DSL.name("target"), SQLDataType.VARCHAR(255).nullable(false), this, "Target");

    /**
     * The column
     * <code>entity_collector.entity_integrations.secondary_target</code>.
     * Secondary target
     */
    public final TableField<EntityIntegrationsRecord, String> SECONDARY_TARGET = createField(DSL.name("secondary_target"), SQLDataType.VARCHAR(255), this, "Secondary target");

    /**
     * The column <code>entity_collector.entity_integrations.in_source</code>.
     * Source
     */
    public final TableField<EntityIntegrationsRecord, String> IN_SOURCE = createField(DSL.name("in_source"), SQLDataType.VARCHAR(255), this, "Source");

    /**
     * The column
     * <code>entity_collector.entity_integrations.in_source_type</code>. Type of
     * source that integration is generated
     */
    public final TableField<EntityIntegrationsRecord, EntityIntegrationsInSourceType> IN_SOURCE_TYPE = createField(DSL.name("in_source_type"), SQLDataType.VARCHAR(13).nullable(false).asEnumDataType(EntityIntegrationsInSourceType.class), this, "Type of source that integration is generated");

    /**
     * The column <code>entity_collector.entity_integrations.created_by</code>.
     * ID of the user who created this row
     */
    public final TableField<EntityIntegrationsRecord, ULong> CREATED_BY = createField(DSL.name("created_by"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>entity_collector.entity_integrations.created_at</code>.
     * Time when this row is created
     */
    public final TableField<EntityIntegrationsRecord, LocalDateTime> CREATED_AT = createField(DSL.name("created_at"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>entity_collector.entity_integrations.updated_by</code>.
     * ID of the user who updated this row
     */
    public final TableField<EntityIntegrationsRecord, ULong> UPDATED_BY = createField(DSL.name("updated_by"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>entity_collector.entity_integrations.updated_at</code>.
     * Time when this row is updated
     */
    public final TableField<EntityIntegrationsRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("updated_at"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private EntityIntegrations(Name alias, Table<EntityIntegrationsRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private EntityIntegrations(Name alias, Table<EntityIntegrationsRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>entity_collector.entity_integrations</code> table
     * reference
     */
    public EntityIntegrations(String alias) {
        this(DSL.name(alias), ENTITY_INTEGRATIONS);
    }

    /**
     * Create an aliased <code>entity_collector.entity_integrations</code> table
     * reference
     */
    public EntityIntegrations(Name alias) {
        this(alias, ENTITY_INTEGRATIONS);
    }

    /**
     * Create a <code>entity_collector.entity_integrations</code> table
     * reference
     */
    public EntityIntegrations() {
        this(DSL.name("entity_integrations"), null);
    }

    public <O extends Record> EntityIntegrations(Table<O> path, ForeignKey<O, EntityIntegrationsRecord> childPath, InverseForeignKey<O, EntityIntegrationsRecord> parentPath) {
        super(path, childPath, parentPath, ENTITY_INTEGRATIONS);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class EntityIntegrationsPath extends EntityIntegrations implements Path<EntityIntegrationsRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> EntityIntegrationsPath(Table<O> path, ForeignKey<O, EntityIntegrationsRecord> childPath, InverseForeignKey<O, EntityIntegrationsRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private EntityIntegrationsPath(Name alias, Table<EntityIntegrationsRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public EntityIntegrationsPath as(String alias) {
            return new EntityIntegrationsPath(DSL.name(alias), this);
        }

        @Override
        public EntityIntegrationsPath as(Name alias) {
            return new EntityIntegrationsPath(alias, this);
        }

        @Override
        public EntityIntegrationsPath as(Table<?> alias) {
            return new EntityIntegrationsPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : EntityCollector.ENTITY_COLLECTOR;
    }

    @Override
    public Identity<EntityIntegrationsRecord, ULong> getIdentity() {
        return (Identity<EntityIntegrationsRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<EntityIntegrationsRecord> getPrimaryKey() {
        return Keys.KEY_ENTITY_INTEGRATIONS_PRIMARY;
    }

    private transient EntityCollectorLogPath _entityCollectorLog;

    /**
     * Get the implicit to-many join path to the
     * <code>entity_collector.entity_collector_log</code> table
     */
    public EntityCollectorLogPath entityCollectorLog() {
        if (_entityCollectorLog == null)
            _entityCollectorLog = new EntityCollectorLogPath(this, null, Keys.FK1_COLLECTOR_ENTITY_INTEGRATION_ID.getInverseKey());

        return _entityCollectorLog;
    }

    @Override
    public EntityIntegrations as(String alias) {
        return new EntityIntegrations(DSL.name(alias), this);
    }

    @Override
    public EntityIntegrations as(Name alias) {
        return new EntityIntegrations(alias, this);
    }

    @Override
    public EntityIntegrations as(Table<?> alias) {
        return new EntityIntegrations(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityIntegrations rename(String name) {
        return new EntityIntegrations(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityIntegrations rename(Name name) {
        return new EntityIntegrations(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityIntegrations rename(Table<?> name) {
        return new EntityIntegrations(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityIntegrations where(Condition condition) {
        return new EntityIntegrations(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityIntegrations where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityIntegrations where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityIntegrations where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityIntegrations where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityIntegrations where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityIntegrations where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityIntegrations where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityIntegrations whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityIntegrations whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
