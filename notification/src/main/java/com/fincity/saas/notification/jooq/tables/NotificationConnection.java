/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables;


import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.jooq.Indexes;
import com.fincity.saas.notification.jooq.Keys;
import com.fincity.saas.notification.jooq.Notification;
import com.fincity.saas.notification.jooq.tables.records.NotificationConnectionRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.JSON;
import org.jooq.Name;
import org.jooq.PlainSQL;
import org.jooq.QueryPart;
import org.jooq.SQL;
import org.jooq.Schema;
import org.jooq.Select;
import org.jooq.Stringly;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationConnection extends TableImpl<NotificationConnectionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>notification.notification_connection</code>
     */
    public static final NotificationConnection NOTIFICATION_CONNECTION = new NotificationConnection();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<NotificationConnectionRecord> getRecordType() {
        return NotificationConnectionRecord.class;
    }

    /**
     * The column <code>notification.notification_connection.ID</code>. Primary
     * key
     */
    public final TableField<NotificationConnectionRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>notification.notification_connection.CLIENT_ID</code>.
     * Identifier for the client. References security_client table
     */
    public final TableField<NotificationConnectionRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the client. References security_client table");

    /**
     * The column <code>notification.notification_connection.APP_ID</code>.
     * Identifier for the application. References security_app table
     */
    public final TableField<NotificationConnectionRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the application. References security_app table");

    /**
     * The column <code>notification.notification_connection.CODE</code>. Code
     */
    public final TableField<NotificationConnectionRecord, String> CODE = createField(DSL.name("CODE"), SQLDataType.CHAR(22).nullable(false), this, "Code");

    /**
     * The column <code>notification.notification_connection.NAME</code>.
     * Connection name
     */
    public final TableField<NotificationConnectionRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.CHAR(125).nullable(false), this, "Connection name");

    /**
     * The column <code>notification.notification_connection.DESCRIPTION</code>.
     * Description of notification connection
     */
    public final TableField<NotificationConnectionRecord, String> DESCRIPTION = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this, "Description of notification connection");

    /**
     * The column
     * <code>notification.notification_connection.CHANNEL_TYPE</code>. Type of
     * notification channel
     */
    public final TableField<NotificationConnectionRecord, NotificationChannelType> CHANNEL_TYPE = createField(DSL.name("CHANNEL_TYPE"), SQLDataType.VARCHAR(11).nullable(false), this, "Type of notification channel", new EnumConverter<String, NotificationChannelType>(String.class, NotificationChannelType.class));

    /**
     * The column
     * <code>notification.notification_connection.CONNECTION_DETAILS</code>.
     * Connection details object
     */
    public final TableField<NotificationConnectionRecord, JSON> CONNECTION_DETAILS = createField(DSL.name("CONNECTION_DETAILS"), SQLDataType.JSON.nullable(false), this, "Connection details object");

    /**
     * The column <code>notification.notification_connection.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public final TableField<NotificationConnectionRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>notification.notification_connection.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<NotificationConnectionRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>notification.notification_connection.UPDATED_BY</code>.
     * ID of the user who updated this row
     */
    public final TableField<NotificationConnectionRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>notification.notification_connection.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public final TableField<NotificationConnectionRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private NotificationConnection(Name alias, Table<NotificationConnectionRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private NotificationConnection(Name alias, Table<NotificationConnectionRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>notification.notification_connection</code> table
     * reference
     */
    public NotificationConnection(String alias) {
        this(DSL.name(alias), NOTIFICATION_CONNECTION);
    }

    /**
     * Create an aliased <code>notification.notification_connection</code> table
     * reference
     */
    public NotificationConnection(Name alias) {
        this(alias, NOTIFICATION_CONNECTION);
    }

    /**
     * Create a <code>notification.notification_connection</code> table
     * reference
     */
    public NotificationConnection() {
        this(DSL.name("notification_connection"), null);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Notification.NOTIFICATION;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.NOTIFICATION_CONNECTION_IDX1_CONNECTION_CODE_CLIENT_ID_APP_ID, Indexes.NOTIFICATION_CONNECTION_IDX2_CONNECTION_CLIENT_ID_APP_ID);
    }

    @Override
    public Identity<NotificationConnectionRecord, ULong> getIdentity() {
        return (Identity<NotificationConnectionRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<NotificationConnectionRecord> getPrimaryKey() {
        return Keys.KEY_NOTIFICATION_CONNECTION_PRIMARY;
    }

    @Override
    public List<UniqueKey<NotificationConnectionRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_NOTIFICATION_CONNECTION_UK1_CONNECTION_CODE);
    }

    @Override
    public NotificationConnection as(String alias) {
        return new NotificationConnection(DSL.name(alias), this);
    }

    @Override
    public NotificationConnection as(Name alias) {
        return new NotificationConnection(alias, this);
    }

    @Override
    public NotificationConnection as(Table<?> alias) {
        return new NotificationConnection(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationConnection rename(String name) {
        return new NotificationConnection(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationConnection rename(Name name) {
        return new NotificationConnection(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationConnection rename(Table<?> name) {
        return new NotificationConnection(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationConnection where(Condition condition) {
        return new NotificationConnection(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationConnection where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationConnection where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationConnection where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationConnection where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationConnection where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationConnection where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationConnection where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationConnection whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationConnection whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
