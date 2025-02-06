/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables;


import com.fincity.saas.notification.jooq.Keys;
import com.fincity.saas.notification.jooq.Notification;
import com.fincity.saas.notification.jooq.tables.NotificationType.NotificationTypePath;
import com.fincity.saas.notification.jooq.tables.records.NotificationAppPreferenceRecord;

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
public class NotificationAppPreference extends TableImpl<NotificationAppPreferenceRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>notification.notification_app_preference</code>
     */
    public static final NotificationAppPreference NOTIFICATION_APP_PREFERENCE = new NotificationAppPreference();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<NotificationAppPreferenceRecord> getRecordType() {
        return NotificationAppPreferenceRecord.class;
    }

    /**
     * The column <code>notification.notification_app_preference.ID</code>.
     * Primary key
     */
    public final TableField<NotificationAppPreferenceRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>notification.notification_app_preference.CLIENT_ID</code>. Client
     * identifier
     */
    public final TableField<NotificationAppPreferenceRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client identifier");

    /**
     * The column <code>notification.notification_app_preference.APP_ID</code>.
     * Application identifier
     */
    public final TableField<NotificationAppPreferenceRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Application identifier");

    /**
     * The column
     * <code>notification.notification_app_preference.NOTIFICATION_TYPE_ID</code>.
     * Reference to notification type
     */
    public final TableField<NotificationAppPreferenceRecord, ULong> NOTIFICATION_TYPE_ID = createField(DSL.name("NOTIFICATION_TYPE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Reference to notification type");

    /**
     * The column
     * <code>notification.notification_app_preference.IS_DISABLED</code>. Flag
     * to disable all notifications for this type at app level
     */
    public final TableField<NotificationAppPreferenceRecord, Byte> IS_DISABLED = createField(DSL.name("IS_DISABLED"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Flag to disable all notifications for this type at app level");

    /**
     * The column
     * <code>notification.notification_app_preference.IS_EMAIL_ENABLED</code>.
     * Flag to enable email notifications at app level
     */
    public final TableField<NotificationAppPreferenceRecord, Byte> IS_EMAIL_ENABLED = createField(DSL.name("IS_EMAIL_ENABLED"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("1", SQLDataType.TINYINT)), this, "Flag to enable email notifications at app level");

    /**
     * The column
     * <code>notification.notification_app_preference.IS_IN_APP_ENABLED</code>.
     * Flag to enable in-app notifications at app level
     */
    public final TableField<NotificationAppPreferenceRecord, Byte> IS_IN_APP_ENABLED = createField(DSL.name("IS_IN_APP_ENABLED"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("1", SQLDataType.TINYINT)), this, "Flag to enable in-app notifications at app level");

    /**
     * The column
     * <code>notification.notification_app_preference.IS_SMS_ENABLED</code>.
     * Flag to enable SMS notifications at app level
     */
    public final TableField<NotificationAppPreferenceRecord, Byte> IS_SMS_ENABLED = createField(DSL.name("IS_SMS_ENABLED"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Flag to enable SMS notifications at app level");

    /**
     * The column
     * <code>notification.notification_app_preference.IS_PUSH_ENABLED</code>.
     * Flag to enable push notifications at app level
     */
    public final TableField<NotificationAppPreferenceRecord, Byte> IS_PUSH_ENABLED = createField(DSL.name("IS_PUSH_ENABLED"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Flag to enable push notifications at app level");

    /**
     * The column
     * <code>notification.notification_app_preference.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<NotificationAppPreferenceRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>notification.notification_app_preference.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<NotificationAppPreferenceRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>notification.notification_app_preference.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<NotificationAppPreferenceRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>notification.notification_app_preference.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<NotificationAppPreferenceRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private NotificationAppPreference(Name alias, Table<NotificationAppPreferenceRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private NotificationAppPreference(Name alias, Table<NotificationAppPreferenceRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>notification.notification_app_preference</code>
     * table reference
     */
    public NotificationAppPreference(String alias) {
        this(DSL.name(alias), NOTIFICATION_APP_PREFERENCE);
    }

    /**
     * Create an aliased <code>notification.notification_app_preference</code>
     * table reference
     */
    public NotificationAppPreference(Name alias) {
        this(alias, NOTIFICATION_APP_PREFERENCE);
    }

    /**
     * Create a <code>notification.notification_app_preference</code> table
     * reference
     */
    public NotificationAppPreference() {
        this(DSL.name("notification_app_preference"), null);
    }

    public <O extends Record> NotificationAppPreference(Table<O> path, ForeignKey<O, NotificationAppPreferenceRecord> childPath, InverseForeignKey<O, NotificationAppPreferenceRecord> parentPath) {
        super(path, childPath, parentPath, NOTIFICATION_APP_PREFERENCE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class NotificationAppPreferencePath extends NotificationAppPreference implements Path<NotificationAppPreferenceRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> NotificationAppPreferencePath(Table<O> path, ForeignKey<O, NotificationAppPreferenceRecord> childPath, InverseForeignKey<O, NotificationAppPreferenceRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private NotificationAppPreferencePath(Name alias, Table<NotificationAppPreferenceRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public NotificationAppPreferencePath as(String alias) {
            return new NotificationAppPreferencePath(DSL.name(alias), this);
        }

        @Override
        public NotificationAppPreferencePath as(Name alias) {
            return new NotificationAppPreferencePath(alias, this);
        }

        @Override
        public NotificationAppPreferencePath as(Table<?> alias) {
            return new NotificationAppPreferencePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Notification.NOTIFICATION;
    }

    @Override
    public Identity<NotificationAppPreferenceRecord, ULong> getIdentity() {
        return (Identity<NotificationAppPreferenceRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<NotificationAppPreferenceRecord> getPrimaryKey() {
        return Keys.KEY_NOTIFICATION_APP_PREFERENCE_PRIMARY;
    }

    @Override
    public List<UniqueKey<NotificationAppPreferenceRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_NOTIFICATION_APP_PREFERENCE_UK1_USER_PREFERENCE_CLIENT_ID_APP_ID_NOTI_TYPE);
    }

    @Override
    public List<ForeignKey<NotificationAppPreferenceRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_PREF_NOTIFICATION_TYPE);
    }

    private transient NotificationTypePath _notificationType;

    /**
     * Get the implicit join path to the
     * <code>notification.notification_type</code> table.
     */
    public NotificationTypePath notificationType() {
        if (_notificationType == null)
            _notificationType = new NotificationTypePath(this, Keys.FK1_APP_PREF_NOTIFICATION_TYPE, null);

        return _notificationType;
    }

    @Override
    public NotificationAppPreference as(String alias) {
        return new NotificationAppPreference(DSL.name(alias), this);
    }

    @Override
    public NotificationAppPreference as(Name alias) {
        return new NotificationAppPreference(alias, this);
    }

    @Override
    public NotificationAppPreference as(Table<?> alias) {
        return new NotificationAppPreference(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationAppPreference rename(String name) {
        return new NotificationAppPreference(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationAppPreference rename(Name name) {
        return new NotificationAppPreference(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationAppPreference rename(Table<?> name) {
        return new NotificationAppPreference(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationAppPreference where(Condition condition) {
        return new NotificationAppPreference(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationAppPreference where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationAppPreference where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationAppPreference where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationAppPreference where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationAppPreference where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationAppPreference where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationAppPreference where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationAppPreference whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationAppPreference whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
