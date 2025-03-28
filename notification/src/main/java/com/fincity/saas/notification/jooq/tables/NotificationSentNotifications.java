/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables;


import com.fincity.saas.commons.jooq.convertor.jooq.converters.JSONtoClassConverter;
import com.fincity.saas.notification.jooq.Keys;
import com.fincity.saas.notification.jooq.Notification;
import com.fincity.saas.notification.jooq.tables.records.NotificationSentNotificationsRecord;
import com.fincity.saas.notification.model.NotificationChannel;
import com.fincity.saas.notification.model.response.NotificationErrorInfo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Identity;
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
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationSentNotifications extends TableImpl<NotificationSentNotificationsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>notification.notification_sent_notifications</code>
     */
    public static final NotificationSentNotifications NOTIFICATION_SENT_NOTIFICATIONS = new NotificationSentNotifications();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<NotificationSentNotificationsRecord> getRecordType() {
        return NotificationSentNotificationsRecord.class;
    }

    /**
     * The column <code>notification.notification_sent_notifications.ID</code>.
     * Primary key
     */
    public final TableField<NotificationSentNotificationsRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>notification.notification_sent_notifications.CODE</code>. Unique
     * Code to identify this row
     */
    public final TableField<NotificationSentNotificationsRecord, String> CODE = createField(DSL.name("CODE"), SQLDataType.CHAR(22).nullable(false), this, "Unique Code to identify this row");

    /**
     * The column
     * <code>notification.notification_sent_notifications.APP_CODE</code>. App
     * Code on which this notification was sent. References security_app table
     */
    public final TableField<NotificationSentNotificationsRecord, String> APP_CODE = createField(DSL.name("APP_CODE"), SQLDataType.CHAR(64).nullable(false), this, "App Code on which this notification was sent. References security_app table");

    /**
     * The column
     * <code>notification.notification_sent_notifications.CLIENT_CODE</code>.
     * Client Code to whom this notification we sent. References security_user
     * table
     */
    public final TableField<NotificationSentNotificationsRecord, String> CLIENT_CODE = createField(DSL.name("CLIENT_CODE"), SQLDataType.CHAR(8).nullable(false), this, "Client Code to whom this notification we sent. References security_user table");

    /**
     * The column
     * <code>notification.notification_sent_notifications.USER_ID</code>.
     * Identifier for the user. References security_user table
     */
    public final TableField<NotificationSentNotificationsRecord, ULong> USER_ID = createField(DSL.name("USER_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the user. References security_user table");

    /**
     * The column
     * <code>notification.notification_sent_notifications.NOTIFICATION_CHANNEL</code>.
     * Notification message that is sent in different channels
     */
    public final TableField<NotificationSentNotificationsRecord, NotificationChannel> NOTIFICATION_CHANNEL = createField(DSL.name("NOTIFICATION_CHANNEL"), SQLDataType.JSON.nullable(false), this, "Notification message that is sent in different channels", new JSONtoClassConverter<JSON, NotificationChannel>(JSON.class, NotificationChannel.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.NOTIFICATION_TYPE</code>.
     * Type of notification that is sent
     */
    public final TableField<NotificationSentNotificationsRecord, String> NOTIFICATION_TYPE = createField(DSL.name("NOTIFICATION_TYPE"), SQLDataType.VARCHAR(11).nullable(false).defaultValue(DSL.inline("INFO", SQLDataType.VARCHAR)), this, "Type of notification that is sent");

    /**
     * The column
     * <code>notification.notification_sent_notifications.NOTIFICATION_STAGE</code>.
     * Stage of the notification that is sent
     */
    public final TableField<NotificationSentNotificationsRecord, String> NOTIFICATION_STAGE = createField(DSL.name("NOTIFICATION_STAGE"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("PLATFORM", SQLDataType.VARCHAR)), this, "Stage of the notification that is sent");

    /**
     * The column
     * <code>notification.notification_sent_notifications.TRIGGER_TIME</code>.
     * Time when the notification was triggered
     */
    public final TableField<NotificationSentNotificationsRecord, LocalDateTime> TRIGGER_TIME = createField(DSL.name("TRIGGER_TIME"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Time when the notification was triggered");

    /**
     * The column
     * <code>notification.notification_sent_notifications.IS_EMAIL</code>. Email
     * notification enabled or not
     */
    public final TableField<NotificationSentNotificationsRecord, Byte> IS_EMAIL = createField(DSL.name("IS_EMAIL"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Email notification enabled or not");

    /**
     * The column
     * <code>notification.notification_sent_notifications.EMAIL_DELIVERY_STATUS</code>.
     * Email delivery status
     */
    public final TableField<NotificationSentNotificationsRecord, Map> EMAIL_DELIVERY_STATUS = createField(DSL.name("EMAIL_DELIVERY_STATUS"), SQLDataType.JSON, this, "Email delivery status", new JSONtoClassConverter<JSON, Map>(JSON.class, Map.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.IS_IN_APP</code>.
     * In-app notification enabled or not
     */
    public final TableField<NotificationSentNotificationsRecord, Byte> IS_IN_APP = createField(DSL.name("IS_IN_APP"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "In-app notification enabled or not");

    /**
     * The column
     * <code>notification.notification_sent_notifications.IN_APP_DELIVERY_STATUS</code>.
     * In-app delivery status
     */
    public final TableField<NotificationSentNotificationsRecord, Map> IN_APP_DELIVERY_STATUS = createField(DSL.name("IN_APP_DELIVERY_STATUS"), SQLDataType.JSON, this, "In-app delivery status", new JSONtoClassConverter<JSON, Map>(JSON.class, Map.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.IS_MOBILE_PUSH</code>.
     * Mobile push notification enabled or not
     */
    public final TableField<NotificationSentNotificationsRecord, Byte> IS_MOBILE_PUSH = createField(DSL.name("IS_MOBILE_PUSH"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Mobile push notification enabled or not");

    /**
     * The column
     * <code>notification.notification_sent_notifications.MOBILE_PUSH_DELIVERY_STATUS</code>.
     * Mobile push delivery status
     */
    public final TableField<NotificationSentNotificationsRecord, Map> MOBILE_PUSH_DELIVERY_STATUS = createField(DSL.name("MOBILE_PUSH_DELIVERY_STATUS"), SQLDataType.JSON, this, "Mobile push delivery status", new JSONtoClassConverter<JSON, Map>(JSON.class, Map.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.IS_WEB_PUSH</code>.
     * Web push notification enabled or not
     */
    public final TableField<NotificationSentNotificationsRecord, Byte> IS_WEB_PUSH = createField(DSL.name("IS_WEB_PUSH"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Web push notification enabled or not");

    /**
     * The column
     * <code>notification.notification_sent_notifications.WEB_PUSH_DELIVERY_STATUS</code>.
     * Web push delivery status
     */
    public final TableField<NotificationSentNotificationsRecord, Map> WEB_PUSH_DELIVERY_STATUS = createField(DSL.name("WEB_PUSH_DELIVERY_STATUS"), SQLDataType.JSON, this, "Web push delivery status", new JSONtoClassConverter<JSON, Map>(JSON.class, Map.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.IS_SMS</code>. SMS
     * notification enabled or not
     */
    public final TableField<NotificationSentNotificationsRecord, Byte> IS_SMS = createField(DSL.name("IS_SMS"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "SMS notification enabled or not");

    /**
     * The column
     * <code>notification.notification_sent_notifications.SMS_DELIVERY_STATUS</code>.
     * SMS delivery status
     */
    public final TableField<NotificationSentNotificationsRecord, Map> SMS_DELIVERY_STATUS = createField(DSL.name("SMS_DELIVERY_STATUS"), SQLDataType.JSON, this, "SMS delivery status", new JSONtoClassConverter<JSON, Map>(JSON.class, Map.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.IS_ERROR</code>. If we
     * are getting error in notification or not
     */
    public final TableField<NotificationSentNotificationsRecord, Byte> IS_ERROR = createField(DSL.name("IS_ERROR"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "If we are getting error in notification or not");

    /**
     * The column
     * <code>notification.notification_sent_notifications.ERROR_INFO</code>.
     * Error info if error occurs during this notification
     */
    public final TableField<NotificationSentNotificationsRecord, NotificationErrorInfo> ERROR_INFO = createField(DSL.name("ERROR_INFO"), SQLDataType.JSON, this, "Error info if error occurs during this notification", new JSONtoClassConverter<JSON, NotificationErrorInfo>(JSON.class, NotificationErrorInfo.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.CHANNEL_ERRORS</code>.
     * Channel Errors if error occurs during channel listeners processing
     */
    public final TableField<NotificationSentNotificationsRecord, Map> CHANNEL_ERRORS = createField(DSL.name("CHANNEL_ERRORS"), SQLDataType.JSON, this, "Channel Errors if error occurs during channel listeners processing", new JSONtoClassConverter<JSON, Map>(JSON.class, Map.class));

    /**
     * The column
     * <code>notification.notification_sent_notifications.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public final TableField<NotificationSentNotificationsRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>notification.notification_sent_notifications.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<NotificationSentNotificationsRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>notification.notification_sent_notifications.UPDATED_BY</code>. ID
     * of the user who updated this row
     */
    public final TableField<NotificationSentNotificationsRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>notification.notification_sent_notifications.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public final TableField<NotificationSentNotificationsRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private NotificationSentNotifications(Name alias, Table<NotificationSentNotificationsRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private NotificationSentNotifications(Name alias, Table<NotificationSentNotificationsRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased
     * <code>notification.notification_sent_notifications</code> table reference
     */
    public NotificationSentNotifications(String alias) {
        this(DSL.name(alias), NOTIFICATION_SENT_NOTIFICATIONS);
    }

    /**
     * Create an aliased
     * <code>notification.notification_sent_notifications</code> table reference
     */
    public NotificationSentNotifications(Name alias) {
        this(alias, NOTIFICATION_SENT_NOTIFICATIONS);
    }

    /**
     * Create a <code>notification.notification_sent_notifications</code> table
     * reference
     */
    public NotificationSentNotifications() {
        this(DSL.name("notification_sent_notifications"), null);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Notification.NOTIFICATION;
    }

    @Override
    public Identity<NotificationSentNotificationsRecord, ULong> getIdentity() {
        return (Identity<NotificationSentNotificationsRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<NotificationSentNotificationsRecord> getPrimaryKey() {
        return Keys.KEY_NOTIFICATION_SENT_NOTIFICATIONS_PRIMARY;
    }

    @Override
    public List<UniqueKey<NotificationSentNotificationsRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_NOTIFICATION_SENT_NOTIFICATIONS_UK1_SENT_NOTIFICATION_CODE);
    }

    @Override
    public NotificationSentNotifications as(String alias) {
        return new NotificationSentNotifications(DSL.name(alias), this);
    }

    @Override
    public NotificationSentNotifications as(Name alias) {
        return new NotificationSentNotifications(alias, this);
    }

    @Override
    public NotificationSentNotifications as(Table<?> alias) {
        return new NotificationSentNotifications(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationSentNotifications rename(String name) {
        return new NotificationSentNotifications(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationSentNotifications rename(Name name) {
        return new NotificationSentNotifications(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public NotificationSentNotifications rename(Table<?> name) {
        return new NotificationSentNotifications(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationSentNotifications where(Condition condition) {
        return new NotificationSentNotifications(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationSentNotifications where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationSentNotifications where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationSentNotifications where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationSentNotifications where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationSentNotifications where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationSentNotifications where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public NotificationSentNotifications where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationSentNotifications whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public NotificationSentNotifications whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
