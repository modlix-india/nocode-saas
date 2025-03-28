/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables.records;


import com.fincity.saas.notification.jooq.tables.NotificationSentNotifications;
import com.fincity.saas.notification.model.request.NotificationChannel;
import com.fincity.saas.notification.model.response.NotificationErrorInfo;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationSentNotificationsRecord extends UpdatableRecordImpl<NotificationSentNotificationsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>notification.notification_sent_notifications.ID</code>.
     * Primary key
     */
    public NotificationSentNotificationsRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_sent_notifications.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.CODE</code>. Unique
     * Code to identify this row
     */
    public NotificationSentNotificationsRecord setCode(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.CODE</code>. Unique
     * Code to identify this row
     */
    public String getCode() {
        return (String) get(1);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.APP_CODE</code>. App
     * Code on which this notification was sent. References security_app table
     */
    public NotificationSentNotificationsRecord setAppCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.APP_CODE</code>. App
     * Code on which this notification was sent. References security_app table
     */
    public String getAppCode() {
        return (String) get(2);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.CLIENT_CODE</code>.
     * Client Code to whom this notification we sent. References security_user
     * table
     */
    public NotificationSentNotificationsRecord setClientCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.CLIENT_CODE</code>.
     * Client Code to whom this notification we sent. References security_user
     * table
     */
    public String getClientCode() {
        return (String) get(3);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.USER_ID</code>.
     * Identifier for the user. References security_user table
     */
    public NotificationSentNotificationsRecord setUserId(ULong value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.USER_ID</code>.
     * Identifier for the user. References security_user table
     */
    public ULong getUserId() {
        return (ULong) get(4);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.NOTIFICATION_CHANNEL</code>.
     * Notification message that is sent in different channels
     */
    public NotificationSentNotificationsRecord setNotificationChannel(NotificationChannel value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.NOTIFICATION_CHANNEL</code>.
     * Notification message that is sent in different channels
     */
    public NotificationChannel getNotificationChannel() {
        return (NotificationChannel) get(5);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.NOTIFICATION_TYPE</code>.
     * Type of notification that is sent
     */
    public NotificationSentNotificationsRecord setNotificationType(String value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.NOTIFICATION_TYPE</code>.
     * Type of notification that is sent
     */
    public String getNotificationType() {
        return (String) get(6);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.NOTIFICATION_STAGE</code>.
     * Stage of the notification that is sent
     */
    public NotificationSentNotificationsRecord setNotificationStage(String value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.NOTIFICATION_STAGE</code>.
     * Stage of the notification that is sent
     */
    public String getNotificationStage() {
        return (String) get(7);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.TRIGGER_TIME</code>.
     * Time when the notification was triggered
     */
    public NotificationSentNotificationsRecord setTriggerTime(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.TRIGGER_TIME</code>.
     * Time when the notification was triggered
     */
    public LocalDateTime getTriggerTime() {
        return (LocalDateTime) get(8);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IS_EMAIL</code>. Email
     * notification enabled or not
     */
    public NotificationSentNotificationsRecord setIsEmail(Byte value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IS_EMAIL</code>. Email
     * notification enabled or not
     */
    public Byte getIsEmail() {
        return (Byte) get(9);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.EMAIL_DELIVERY_STATUS</code>.
     * Email delivery status
     */
    public NotificationSentNotificationsRecord setEmailDeliveryStatus(Map value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.EMAIL_DELIVERY_STATUS</code>.
     * Email delivery status
     */
    public Map getEmailDeliveryStatus() {
        return (Map) get(10);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IS_IN_APP</code>.
     * In-app notification enabled or not
     */
    public NotificationSentNotificationsRecord setIsInApp(Byte value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IS_IN_APP</code>.
     * In-app notification enabled or not
     */
    public Byte getIsInApp() {
        return (Byte) get(11);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IN_APP_DELIVERY_STATUS</code>.
     * In-app delivery status
     */
    public NotificationSentNotificationsRecord setInAppDeliveryStatus(Map value) {
        set(12, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IN_APP_DELIVERY_STATUS</code>.
     * In-app delivery status
     */
    public Map getInAppDeliveryStatus() {
        return (Map) get(12);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IS_MOBILE_PUSH</code>.
     * Mobile push notification enabled or not
     */
    public NotificationSentNotificationsRecord setIsMobilePush(Byte value) {
        set(13, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IS_MOBILE_PUSH</code>.
     * Mobile push notification enabled or not
     */
    public Byte getIsMobilePush() {
        return (Byte) get(13);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.MOBILE_PUSH_DELIVERY_STATUS</code>.
     * Mobile push delivery status
     */
    public NotificationSentNotificationsRecord setMobilePushDeliveryStatus(Map value) {
        set(14, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.MOBILE_PUSH_DELIVERY_STATUS</code>.
     * Mobile push delivery status
     */
    public Map getMobilePushDeliveryStatus() {
        return (Map) get(14);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IS_WEB_PUSH</code>.
     * Web push notification enabled or not
     */
    public NotificationSentNotificationsRecord setIsWebPush(Byte value) {
        set(15, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IS_WEB_PUSH</code>.
     * Web push notification enabled or not
     */
    public Byte getIsWebPush() {
        return (Byte) get(15);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.WEB_PUSH_DELIVERY_STATUS</code>.
     * Web push delivery status
     */
    public NotificationSentNotificationsRecord setWebPushDeliveryStatus(Map value) {
        set(16, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.WEB_PUSH_DELIVERY_STATUS</code>.
     * Web push delivery status
     */
    public Map getWebPushDeliveryStatus() {
        return (Map) get(16);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IS_SMS</code>. SMS
     * notification enabled or not
     */
    public NotificationSentNotificationsRecord setIsSms(Byte value) {
        set(17, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IS_SMS</code>. SMS
     * notification enabled or not
     */
    public Byte getIsSms() {
        return (Byte) get(17);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.SMS_DELIVERY_STATUS</code>.
     * SMS delivery status
     */
    public NotificationSentNotificationsRecord setSmsDeliveryStatus(Map value) {
        set(18, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.SMS_DELIVERY_STATUS</code>.
     * SMS delivery status
     */
    public Map getSmsDeliveryStatus() {
        return (Map) get(18);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.IS_ERROR</code>. If we
     * are getting error in notification or not
     */
    public NotificationSentNotificationsRecord setIsError(Byte value) {
        set(19, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.IS_ERROR</code>. If we
     * are getting error in notification or not
     */
    public Byte getIsError() {
        return (Byte) get(19);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.ERROR_INFO</code>.
     * Error info if error occurs during this notification
     */
    public NotificationSentNotificationsRecord setErrorInfo(NotificationErrorInfo value) {
        set(20, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.ERROR_INFO</code>.
     * Error info if error occurs during this notification
     */
    public NotificationErrorInfo getErrorInfo() {
        return (NotificationErrorInfo) get(20);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.CHANNEL_ERRORS</code>.
     * Channel Errors if error occurs during channel listeners processing
     */
    public NotificationSentNotificationsRecord setChannelErrors(Map value) {
        set(21, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.CHANNEL_ERRORS</code>.
     * Channel Errors if error occurs during channel listeners processing
     */
    public Map getChannelErrors() {
        return (Map) get(21);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public NotificationSentNotificationsRecord setCreatedBy(ULong value) {
        set(22, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(22);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.CREATED_AT</code>.
     * Time when this row is created
     */
    public NotificationSentNotificationsRecord setCreatedAt(LocalDateTime value) {
        set(23, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.CREATED_AT</code>.
     * Time when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(23);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.UPDATED_BY</code>. ID
     * of the user who updated this row
     */
    public NotificationSentNotificationsRecord setUpdatedBy(ULong value) {
        set(24, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.UPDATED_BY</code>. ID
     * of the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(24);
    }

    /**
     * Setter for
     * <code>notification.notification_sent_notifications.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public NotificationSentNotificationsRecord setUpdatedAt(LocalDateTime value) {
        set(25, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_sent_notifications.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(25);
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
     * Create a detached NotificationSentNotificationsRecord
     */
    public NotificationSentNotificationsRecord() {
        super(NotificationSentNotifications.NOTIFICATION_SENT_NOTIFICATIONS);
    }

    /**
     * Create a detached, initialised NotificationSentNotificationsRecord
     */
    public NotificationSentNotificationsRecord(ULong id, String code, String appCode, String clientCode, ULong userId, NotificationChannel notificationChannel, String notificationType, String notificationStage, LocalDateTime triggerTime, Byte isEmail, Map emailDeliveryStatus, Byte isInApp, Map inAppDeliveryStatus, Byte isMobilePush, Map mobilePushDeliveryStatus, Byte isWebPush, Map webPushDeliveryStatus, Byte isSms, Map smsDeliveryStatus, Byte isError, NotificationErrorInfo errorInfo, Map channelErrors, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(NotificationSentNotifications.NOTIFICATION_SENT_NOTIFICATIONS);

        setId(id);
        setCode(code);
        setAppCode(appCode);
        setClientCode(clientCode);
        setUserId(userId);
        setNotificationChannel(notificationChannel);
        setNotificationType(notificationType);
        setNotificationStage(notificationStage);
        setTriggerTime(triggerTime);
        setIsEmail(isEmail);
        setEmailDeliveryStatus(emailDeliveryStatus);
        setIsInApp(isInApp);
        setInAppDeliveryStatus(inAppDeliveryStatus);
        setIsMobilePush(isMobilePush);
        setMobilePushDeliveryStatus(mobilePushDeliveryStatus);
        setIsWebPush(isWebPush);
        setWebPushDeliveryStatus(webPushDeliveryStatus);
        setIsSms(isSms);
        setSmsDeliveryStatus(smsDeliveryStatus);
        setIsError(isError);
        setErrorInfo(errorInfo);
        setChannelErrors(channelErrors);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
