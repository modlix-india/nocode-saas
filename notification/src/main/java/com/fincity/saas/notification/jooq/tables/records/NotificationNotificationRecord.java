/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables.records;


import com.fincity.saas.notification.jooq.tables.NotificationNotification;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationNotificationRecord extends UpdatableRecordImpl<NotificationNotificationRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>notification.notification_notification.ID</code>.
     * Primary key
     */
    public NotificationNotificationRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_notification.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>notification.notification_notification.CLIENT_ID</code>.
     * Identifier for the client. References security_client table
     */
    public NotificationNotificationRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_notification.CLIENT_ID</code>.
     * Identifier for the client. References security_client table
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>notification.notification_notification.APP_ID</code>.
     * Identifier for the application. References security_app table
     */
    public NotificationNotificationRecord setAppId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_notification.APP_ID</code>.
     * Identifier for the application. References security_app table
     */
    public ULong getAppId() {
        return (ULong) get(2);
    }

    /**
     * Setter for <code>notification.notification_notification.CODE</code>. Code
     */
    public NotificationNotificationRecord setCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_notification.CODE</code>. Code
     */
    public String getCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>notification.notification_notification.NAME</code>.
     * Template name
     */
    public NotificationNotificationRecord setName(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_notification.NAME</code>.
     * Template name
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.DESCRIPTION</code>.
     * Description of notification Template
     */
    public NotificationNotificationRecord setDescription(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.DESCRIPTION</code>.
     * Description of notification Template
     */
    public String getDescription() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.NOTIFICATION_TYPE_ID</code>.
     * Identifier for the notification type. References notification_type table
     */
    public NotificationNotificationRecord setNotificationTypeId(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.NOTIFICATION_TYPE_ID</code>.
     * Identifier for the notification type. References notification_type table
     */
    public ULong getNotificationTypeId() {
        return (ULong) get(6);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.EMAIL_TEMPLATE_ID</code>.
     * Identifier for the email template. References notification_template table
     */
    public NotificationNotificationRecord setEmailTemplateId(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.EMAIL_TEMPLATE_ID</code>.
     * Identifier for the email template. References notification_template table
     */
    public ULong getEmailTemplateId() {
        return (ULong) get(7);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.IN_APP_TEMPLATE_ID</code>.
     * Identifier for the inApp template. References notification_template table
     */
    public NotificationNotificationRecord setInAppTemplateId(ULong value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.IN_APP_TEMPLATE_ID</code>.
     * Identifier for the inApp template. References notification_template table
     */
    public ULong getInAppTemplateId() {
        return (ULong) get(8);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.SMS_TEMPLATE_ID</code>.
     * Identifier for the sms template. References notification_template table
     */
    public NotificationNotificationRecord setSmsTemplateId(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.SMS_TEMPLATE_ID</code>.
     * Identifier for the sms template. References notification_template table
     */
    public ULong getSmsTemplateId() {
        return (ULong) get(9);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.PUSH_TEMPLATE_ID</code>.
     * Identifier for the push template. References notification_template table
     */
    public NotificationNotificationRecord setPushTemplateId(ULong value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.PUSH_TEMPLATE_ID</code>.
     * Identifier for the push template. References notification_template table
     */
    public ULong getPushTemplateId() {
        return (ULong) get(10);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public NotificationNotificationRecord setCreatedBy(ULong value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(11);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.CREATED_AT</code>. Time when
     * this row is created
     */
    public NotificationNotificationRecord setCreatedAt(LocalDateTime value) {
        set(12, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.CREATED_AT</code>. Time when
     * this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(12);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public NotificationNotificationRecord setUpdatedBy(ULong value) {
        set(13, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(13);
    }

    /**
     * Setter for
     * <code>notification.notification_notification.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public NotificationNotificationRecord setUpdatedAt(LocalDateTime value) {
        set(14, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_notification.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(14);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<ULong> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached NotificationNotificationRecord
     */
    public NotificationNotificationRecord() {
        super(NotificationNotification.NOTIFICATION_NOTIFICATION);
    }

    /**
     * Create a detached, initialised NotificationNotificationRecord
     */
    public NotificationNotificationRecord(ULong id, ULong clientId, ULong appId, String code, String name, String description, ULong notificationTypeId, ULong emailTemplateId, ULong inAppTemplateId, ULong smsTemplateId, ULong pushTemplateId, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(NotificationNotification.NOTIFICATION_NOTIFICATION);

        setId(id);
        setClientId(clientId);
        setAppId(appId);
        setCode(code);
        setName(name);
        setDescription(description);
        setNotificationTypeId(notificationTypeId);
        setEmailTemplateId(emailTemplateId);
        setInAppTemplateId(inAppTemplateId);
        setSmsTemplateId(smsTemplateId);
        setPushTemplateId(pushTemplateId);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
