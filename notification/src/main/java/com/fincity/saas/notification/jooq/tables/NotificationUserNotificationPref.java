/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables;


import com.fincity.saas.notification.jooq.Keys;
import com.fincity.saas.notification.jooq.Notification;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserNotificationPrefRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Identity;
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
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationUserNotificationPref extends TableImpl<NotificationUserNotificationPrefRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>notification.notification_user_notification_pref</code>
     */
    public static final NotificationUserNotificationPref NOTIFICATION_USER_NOTIFICATION_PREF = new NotificationUserNotificationPref();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<NotificationUserNotificationPrefRecord> getRecordType() {
        return NotificationUserNotificationPrefRecord.class;
    }

    /**
     * The column
     * <code>notification.notification_user_notification_pref.ID</code>. Primary
     * key
     */
    public final TableField<NotificationUserNotificationPrefRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.APP_ID</code>.
     * Identifier for the application. References security_app table
     */
    public final TableField<NotificationUserNotificationPrefRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the application. References security_app table");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.USER_ID</code>.
     * Identifier for the user. References security_user table
     */
    public final TableField<NotificationUserNotificationPrefRecord, ULong> USER_ID = createField(DSL.name("USER_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the user. References security_user table");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.CODE</code>.
     * Unique Code to identify this row
     */
    public final TableField<NotificationUserNotificationPrefRecord, String> CODE = createField(DSL.name("CODE"), SQLDataType.CHAR(22).nullable(false), this, "Unique Code to identify this row");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.NOTIFICATION_NAME</code>.
     * Notification name preference
     */
    public final TableField<NotificationUserNotificationPrefRecord, String> NOTIFICATION_NAME = createField(DSL.name("NOTIFICATION_NAME"), SQLDataType.CHAR(125).nullable(false), this, "Notification name preference");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.ENABLED</code>.
     * Notification name enabled or not
     */
    public final TableField<NotificationUserNotificationPrefRecord, Byte> ENABLED = createField(DSL.name("ENABLED"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Notification name enabled or not");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public final TableField<NotificationUserNotificationPrefRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<NotificationUserNotificationPrefRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.UPDATED_BY</code>.
     * ID of the user who updated this row
     */
    public final TableField<NotificationUserNotificationPrefRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>notification.notification_user_notification_pref.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public final TableField<NotificationUserNotificationPrefRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private NotificationUserNotificationPref(Name alias, Table<NotificationUserNotificationPrefRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private NotificationUserNotificationPref(Name alias, Table<NotificationUserNotificationPrefRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased
     * <code>notification.notification_user_notification_pref</code> table
     * reference
     */
    public NotificationUserNotificationPref(String alias) {
        this(DSL.name(alias), NOTIFICATION_USER_NOTIFICATION_PREF);
    }

    /**
     * Create an aliased
     * <code>notification.notification_user_notification_pref</code> table
     * reference
     */
    public NotificationUserNotificationPref(Name alias) {
        this(alias, NOTIFICATION_USER_NOTIFICATION_PREF);
    }

    /**
     * Create a <code>notification.notification_user_notification_pref</code>
     * table reference
     */
    public NotificationUserNotificationPref() {
        this(DSL.name("notification_user_notification_pref"), null);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Notification.NOTIFICATION;
    }

    @Override
    public Identity<NotificationUserNotificationPrefRecord, ULong> getIdentity() {
        return (Identity<NotificationUserNotificationPrefRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<NotificationUserNotificationPrefRecord> getPrimaryKey() {
        return Keys.KEY_NOTIFICATION_USER_NOTIFICATION_PREF_PRIMARY;
    }

    @Override
    public List<UniqueKey<NotificationUserNotificationPrefRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_NOTIFICATION_USER_NOTIFICATION_PREF_UK1_USER_PREF_CODE, Keys.KEY_NOTIFICATION_USER_NOTIFICATION_PREF_UK2_USER_NOTI_PREF_APP_ID_USER_ID_NAME);
    }

    @Override
    public NotificationUserNotificationPref as(String alias) {
        return new NotificationUserNotificationPref(DSL.name(alias), this);
    }

    @Override
    public NotificationUserNotificationPref as(Name alias) {
        return new NotificationUserNotificationPref(alias, this);
    }

    @Override
    public NotificationUserNotificationPref as(Table<?> alias) {
        return new NotificationUserNotificationPref(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationUserNotificationPref rename(String name) {
        return new NotificationUserNotificationPref(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationUserNotificationPref rename(Name name) {
        return new NotificationUserNotificationPref(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationUserNotificationPref rename(Table<?> name) {
        return new NotificationUserNotificationPref(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationUserNotificationPref where(Condition condition) {
        return new NotificationUserNotificationPref(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationUserNotificationPref where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationUserNotificationPref where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationUserNotificationPref where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationUserNotificationPref where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationUserNotificationPref where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationUserNotificationPref where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationUserNotificationPref where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationUserNotificationPref whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationUserNotificationPref whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
