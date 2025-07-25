/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.processor.jooq.tables;


import com.fincity.saas.entity.processor.jooq.EntityProcessor;
import com.fincity.saas.entity.processor.jooq.Keys;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorNotes.EntityProcessorNotesPath;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTasks.EntityProcessorTasksPath;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.EntityProcessorTicketsPath;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorOwnersRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
public class EntityProcessorOwners extends TableImpl<EntityProcessorOwnersRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>entity_processor.entity_processor_owners</code>
     */
    public static final EntityProcessorOwners ENTITY_PROCESSOR_OWNERS = new EntityProcessorOwners();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<EntityProcessorOwnersRecord> getRecordType() {
        return EntityProcessorOwnersRecord.class;
    }

    /**
     * The column <code>entity_processor.entity_processor_owners.ID</code>.
     * Primary key.
     */
    public final TableField<EntityProcessorOwnersRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.APP_CODE</code>. App Code
     * on which this notification was sent.
     */
    public final TableField<EntityProcessorOwnersRecord, String> APP_CODE = createField(DSL.name("APP_CODE"), SQLDataType.CHAR(64).nullable(false), this, "App Code on which this notification was sent.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.CLIENT_CODE</code>. Client
     * Code to whom this notification we sent.
     */
    public final TableField<EntityProcessorOwnersRecord, String> CLIENT_CODE = createField(DSL.name("CLIENT_CODE"), SQLDataType.CHAR(8).nullable(false), this, "Client Code to whom this notification we sent.");

    /**
     * The column <code>entity_processor.entity_processor_owners.CODE</code>.
     * Unique Code to identify this row.
     */
    public final TableField<EntityProcessorOwnersRecord, String> CODE = createField(DSL.name("CODE"), SQLDataType.CHAR(22).nullable(false), this, "Unique Code to identify this row.");

    /**
     * The column <code>entity_processor.entity_processor_owners.NAME</code>.
     * Name of the Owner. Owner can be anything which will have entities. For
     * Example, Lead and opportunity, Epic and Task, Account and lead.
     */
    public final TableField<EntityProcessorOwnersRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.VARCHAR(512).nullable(false), this, "Name of the Owner. Owner can be anything which will have entities. For Example, Lead and opportunity, Epic and Task, Account and lead.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.DESCRIPTION</code>.
     * Description for the Owner.
     */
    public final TableField<EntityProcessorOwnersRecord, String> DESCRIPTION = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this, "Description for the Owner.");

    /**
     * The column <code>entity_processor.entity_processor_owners.VERSION</code>.
     * Version of this row.
     */
    public final TableField<EntityProcessorOwnersRecord, ULong> VERSION = createField(DSL.name("VERSION"), SQLDataType.BIGINTUNSIGNED.nullable(false).defaultValue(DSL.inline("1", SQLDataType.BIGINTUNSIGNED)), this, "Version of this row.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.DIAL_CODE</code>. Dial
     * code of the phone number this owner has.
     */
    public final TableField<EntityProcessorOwnersRecord, Short> DIAL_CODE = createField(DSL.name("DIAL_CODE"), SQLDataType.SMALLINT.defaultValue(DSL.inline("91", SQLDataType.SMALLINT)), this, "Dial code of the phone number this owner has.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.PHONE_NUMBER</code>. Phone
     * number related to this owner.
     */
    public final TableField<EntityProcessorOwnersRecord, String> PHONE_NUMBER = createField(DSL.name("PHONE_NUMBER"), SQLDataType.CHAR(15), this, "Phone number related to this owner.");

    /**
     * The column <code>entity_processor.entity_processor_owners.EMAIL</code>.
     * Email related to this owner.
     */
    public final TableField<EntityProcessorOwnersRecord, String> EMAIL = createField(DSL.name("EMAIL"), SQLDataType.VARCHAR(512), this, "Email related to this owner.");

    /**
     * The column <code>entity_processor.entity_processor_owners.SOURCE</code>.
     * Source of this owner
     */
    public final TableField<EntityProcessorOwnersRecord, String> SOURCE = createField(DSL.name("SOURCE"), SQLDataType.CHAR(64).nullable(false), this, "Source of this owner");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.SUB_SOURCE</code>. Sub
     * Source of this owner.
     */
    public final TableField<EntityProcessorOwnersRecord, String> SUB_SOURCE = createField(DSL.name("SUB_SOURCE"), SQLDataType.CHAR(64), this, "Sub Source of this owner.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.TEMP_ACTIVE</code>.
     * Temporary active flag for this product.
     */
    public final TableField<EntityProcessorOwnersRecord, Byte> TEMP_ACTIVE = createField(DSL.name("TEMP_ACTIVE"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Temporary active flag for this product.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.IS_ACTIVE</code>. Flag to
     * check if this product is active or not.
     */
    public final TableField<EntityProcessorOwnersRecord, Byte> IS_ACTIVE = createField(DSL.name("IS_ACTIVE"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("1", SQLDataType.TINYINT)), this, "Flag to check if this product is active or not.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.CREATED_BY</code>. ID of
     * the user who created this row.
     */
    public final TableField<EntityProcessorOwnersRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.CREATED_AT</code>. Time
     * when this row is created.
     */
    public final TableField<EntityProcessorOwnersRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.UPDATED_BY</code>. ID of
     * the user who updated this row.
     */
    public final TableField<EntityProcessorOwnersRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row.");

    /**
     * The column
     * <code>entity_processor.entity_processor_owners.UPDATED_AT</code>. Time
     * when this row is updated.
     */
    public final TableField<EntityProcessorOwnersRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated.");

    private EntityProcessorOwners(Name alias, Table<EntityProcessorOwnersRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private EntityProcessorOwners(Name alias, Table<EntityProcessorOwnersRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>entity_processor.entity_processor_owners</code>
     * table reference
     */
    public EntityProcessorOwners(String alias) {
        this(DSL.name(alias), ENTITY_PROCESSOR_OWNERS);
    }

    /**
     * Create an aliased <code>entity_processor.entity_processor_owners</code>
     * table reference
     */
    public EntityProcessorOwners(Name alias) {
        this(alias, ENTITY_PROCESSOR_OWNERS);
    }

    /**
     * Create a <code>entity_processor.entity_processor_owners</code> table
     * reference
     */
    public EntityProcessorOwners() {
        this(DSL.name("entity_processor_owners"), null);
    }

    public <O extends Record> EntityProcessorOwners(Table<O> path, ForeignKey<O, EntityProcessorOwnersRecord> childPath, InverseForeignKey<O, EntityProcessorOwnersRecord> parentPath) {
        super(path, childPath, parentPath, ENTITY_PROCESSOR_OWNERS);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class EntityProcessorOwnersPath extends EntityProcessorOwners implements Path<EntityProcessorOwnersRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> EntityProcessorOwnersPath(Table<O> path, ForeignKey<O, EntityProcessorOwnersRecord> childPath, InverseForeignKey<O, EntityProcessorOwnersRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private EntityProcessorOwnersPath(Name alias, Table<EntityProcessorOwnersRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public EntityProcessorOwnersPath as(String alias) {
            return new EntityProcessorOwnersPath(DSL.name(alias), this);
        }

        @Override
        public EntityProcessorOwnersPath as(Name alias) {
            return new EntityProcessorOwnersPath(alias, this);
        }

        @Override
        public EntityProcessorOwnersPath as(Table<?> alias) {
            return new EntityProcessorOwnersPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : EntityProcessor.ENTITY_PROCESSOR;
    }

    @Override
    public Identity<EntityProcessorOwnersRecord, ULong> getIdentity() {
        return (Identity<EntityProcessorOwnersRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<EntityProcessorOwnersRecord> getPrimaryKey() {
        return Keys.KEY_ENTITY_PROCESSOR_OWNERS_PRIMARY;
    }

    @Override
    public List<UniqueKey<EntityProcessorOwnersRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_ENTITY_PROCESSOR_OWNERS_UK1_OWNERS_CODE);
    }

    private transient EntityProcessorNotesPath _entityProcessorNotes;

    /**
     * Get the implicit to-many join path to the
     * <code>entity_processor.entity_processor_notes</code> table
     */
    public EntityProcessorNotesPath entityProcessorNotes() {
        if (_entityProcessorNotes == null)
            _entityProcessorNotes = new EntityProcessorNotesPath(this, null, Keys.FK1_NOTES_OWNER_ID.getInverseKey());

        return _entityProcessorNotes;
    }

    private transient EntityProcessorTicketsPath _entityProcessorTickets;

    /**
     * Get the implicit to-many join path to the
     * <code>entity_processor.entity_processor_tickets</code> table
     */
    public EntityProcessorTicketsPath entityProcessorTickets() {
        if (_entityProcessorTickets == null)
            _entityProcessorTickets = new EntityProcessorTicketsPath(this, null, Keys.FK1_TICKETS_OWNER_ID.getInverseKey());

        return _entityProcessorTickets;
    }

    private transient EntityProcessorTasksPath _entityProcessorTasks;

    /**
     * Get the implicit to-many join path to the
     * <code>entity_processor.entity_processor_tasks</code> table
     */
    public EntityProcessorTasksPath entityProcessorTasks() {
        if (_entityProcessorTasks == null)
            _entityProcessorTasks = new EntityProcessorTasksPath(this, null, Keys.FK2_TASKS_OWNER_ID.getInverseKey());

        return _entityProcessorTasks;
    }

    @Override
    public EntityProcessorOwners as(String alias) {
        return new EntityProcessorOwners(DSL.name(alias), this);
    }

    @Override
    public EntityProcessorOwners as(Name alias) {
        return new EntityProcessorOwners(alias, this);
    }

    @Override
    public EntityProcessorOwners as(Table<?> alias) {
        return new EntityProcessorOwners(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityProcessorOwners rename(String name) {
        return new EntityProcessorOwners(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityProcessorOwners rename(Name name) {
        return new EntityProcessorOwners(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public EntityProcessorOwners rename(Table<?> name) {
        return new EntityProcessorOwners(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityProcessorOwners where(Condition condition) {
        return new EntityProcessorOwners(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityProcessorOwners where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityProcessorOwners where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityProcessorOwners where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityProcessorOwners where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityProcessorOwners where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityProcessorOwners where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public EntityProcessorOwners where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityProcessorOwners whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public EntityProcessorOwners whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
