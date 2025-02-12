/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables;


import com.fincity.saas.notification.jooq.Indexes;
import com.fincity.saas.notification.jooq.Keys;
import com.fincity.saas.notification.jooq.Notification;
import com.fincity.saas.notification.jooq.tables.NotificationAppPreference.NotificationAppPreferencePath;
import com.fincity.saas.notification.jooq.tables.NotificationNotification.NotificationNotificationPath;
import com.fincity.saas.notification.jooq.tables.NotificationUserPreference.NotificationUserPreferencePath;
import com.fincity.saas.notification.jooq.tables.records.NotificationTypeRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
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
public class NotificationType extends TableImpl<NotificationTypeRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>notification.notification_type</code>
     */
    public static final NotificationType NOTIFICATION_TYPE = new NotificationType();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<NotificationTypeRecord> getRecordType() {
        return NotificationTypeRecord.class;
    }

    /**
     * The column <code>notification.notification_type.ID</code>. Primary key
     */
    public final TableField<NotificationTypeRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>notification.notification_type.CLIENT_ID</code>.
     * Identifier for the client. References security_client table
     */
    public final TableField<NotificationTypeRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the client. References security_client table");

    /**
     * The column <code>notification.notification_type.APP_ID</code>. Identifier
     * for the application. References security_app table
     */
    public final TableField<NotificationTypeRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the application. References security_app table");

    /**
     * The column <code>notification.notification_type.CODE</code>. Code
     */
    public final TableField<NotificationTypeRecord, String> CODE = createField(DSL.name("CODE"), SQLDataType.CHAR(22).nullable(false), this, "Code");

    /**
     * The column <code>notification.notification_type.NAME</code>. Notification
     * type name
     */
    public final TableField<NotificationTypeRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.CHAR(125).nullable(false), this, "Notification type name");

    /**
     * The column <code>notification.notification_type.DESCRIPTION</code>.
     * Description of notification type
     */
    public final TableField<NotificationTypeRecord, String> DESCRIPTION = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this, "Description of notification type");

    /**
     * The column <code>notification.notification_type.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<NotificationTypeRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>notification.notification_type.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<NotificationTypeRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>notification.notification_type.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<NotificationTypeRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>notification.notification_type.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<NotificationTypeRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private NotificationType(Name alias, Table<NotificationTypeRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private NotificationType(Name alias, Table<NotificationTypeRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>notification.notification_type</code> table
     * reference
     */
    public NotificationType(String alias) {
        this(DSL.name(alias), NOTIFICATION_TYPE);
    }

    /**
     * Create an aliased <code>notification.notification_type</code> table
     * reference
     */
    public NotificationType(Name alias) {
        this(alias, NOTIFICATION_TYPE);
    }

    /**
     * Create a <code>notification.notification_type</code> table reference
     */
    public NotificationType() {
        this(DSL.name("notification_type"), null);
    }

    public <O extends Record> NotificationType(Table<O> path, ForeignKey<O, NotificationTypeRecord> childPath, InverseForeignKey<O, NotificationTypeRecord> parentPath) {
        super(path, childPath, parentPath, NOTIFICATION_TYPE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class NotificationTypePath extends NotificationType implements Path<NotificationTypeRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> NotificationTypePath(Table<O> path, ForeignKey<O, NotificationTypeRecord> childPath, InverseForeignKey<O, NotificationTypeRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private NotificationTypePath(Name alias, Table<NotificationTypeRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public NotificationTypePath as(String alias) {
            return new NotificationTypePath(DSL.name(alias), this);
        }

        @Override
        public NotificationTypePath as(Name alias) {
            return new NotificationTypePath(alias, this);
        }

        @Override
        public NotificationTypePath as(Table<?> alias) {
            return new NotificationTypePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Notification.NOTIFICATION;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.NOTIFICATION_TYPE_IDX1_TYPE_CODE_CLIENT_ID_APP_ID, Indexes.NOTIFICATION_TYPE_IDX2_TYPE_CLIENT_ID_APP_ID);
    }

    @Override
    public Identity<NotificationTypeRecord, ULong> getIdentity() {
        return (Identity<NotificationTypeRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<NotificationTypeRecord> getPrimaryKey() {
        return Keys.KEY_NOTIFICATION_TYPE_PRIMARY;
    }

    @Override
    public List<UniqueKey<NotificationTypeRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_NOTIFICATION_TYPE_UK1_TYPE_CODE);
    }

    private transient NotificationAppPreferencePath _notificationAppPreference;

    /**
     * Get the implicit to-many join path to the
     * <code>notification.notification_app_preference</code> table
     */
    public NotificationAppPreferencePath notificationAppPreference() {
        if (_notificationAppPreference == null)
            _notificationAppPreference = new NotificationAppPreferencePath(this, null, Keys.FK1_APP_PREF_NOTIFICATION_TYPE_ID.getInverseKey());

        return _notificationAppPreference;
    }

    private transient NotificationNotificationPath _notificationNotification;

    /**
     * Get the implicit to-many join path to the
     * <code>notification.notification_notification</code> table
     */
    public NotificationNotificationPath notificationNotification() {
        if (_notificationNotification == null)
            _notificationNotification = new NotificationNotificationPath(this, null, Keys.FK1_NOTIFICATION_NOTIFICATION_TYPE.getInverseKey());

        return _notificationNotification;
    }

    private transient NotificationUserPreferencePath _notificationUserPreference;

    /**
     * Get the implicit to-many join path to the
     * <code>notification.notification_user_preference</code> table
     */
    public NotificationUserPreferencePath notificationUserPreference() {
        if (_notificationUserPreference == null)
            _notificationUserPreference = new NotificationUserPreferencePath(this, null, Keys.FK1_USER_PREF_NOTIFICATION_TYPE_ID.getInverseKey());

        return _notificationUserPreference;
    }

    @Override
    public NotificationType as(String alias) {
        return new NotificationType(DSL.name(alias), this);
    }

    @Override
    public NotificationType as(Name alias) {
        return new NotificationType(alias, this);
    }

    @Override
    public NotificationType as(Table<?> alias) {
        return new NotificationType(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationType rename(String name) {
        return new NotificationType(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationType rename(Name name) {
        return new NotificationType(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationType rename(Table<?> name) {
        return new NotificationType(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationType where(Condition condition) {
        return new NotificationType(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationType where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationType where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationType where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationType where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationType where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationType where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationType where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationType whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationType whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
