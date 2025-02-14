/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables.records;


import com.fincity.saas.notification.jooq.tables.NotificationUserPreference;

import java.time.LocalDateTime;

import org.jooq.JSON;
import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationUserPreferenceRecord extends UpdatableRecordImpl<NotificationUserPreferenceRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>notification.notification_user_preference.ID</code>.
     * Primary key
     */
    public NotificationUserPreferenceRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_user_preference.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>notification.notification_user_preference.APP_ID</code>.
     * Identifier for the application. References security_app table
     */
    public NotificationUserPreferenceRecord setAppId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_user_preference.APP_ID</code>.
     * Identifier for the application. References security_app table
     */
    public ULong getAppId() {
        return (ULong) get(1);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preference.USER_ID</code>.
     * Identifier for the user. References security_user table
     */
    public NotificationUserPreferenceRecord setUserId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preference.USER_ID</code>.
     * Identifier for the user. References security_user table
     */
    public ULong getUserId() {
        return (ULong) get(2);
    }

    /**
     * Setter for <code>notification.notification_user_preference.CODE</code>.
     * Unique Code to identify this row
     */
    public NotificationUserPreferenceRecord setCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_user_preference.CODE</code>.
     * Unique Code to identify this row
     */
    public String getCode() {
        return (String) get(3);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preference.PREFERENCES</code>.
     * Notification user preferences
     */
    public NotificationUserPreferenceRecord setPreferences(JSON value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preference.PREFERENCES</code>.
     * Notification user preferences
     */
    public JSON getPreferences() {
        return (JSON) get(4);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preference.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public NotificationUserPreferenceRecord setCreatedBy(ULong value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preference.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(5);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preference.CREATED_AT</code>. Time
     * when this row is created
     */
    public NotificationUserPreferenceRecord setCreatedAt(LocalDateTime value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preference.CREATED_AT</code>. Time
     * when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(6);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preference.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public NotificationUserPreferenceRecord setUpdatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preference.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preference.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public NotificationUserPreferenceRecord setUpdatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preference.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(8);
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
     * Create a detached NotificationUserPreferenceRecord
     */
    public NotificationUserPreferenceRecord() {
        super(NotificationUserPreference.NOTIFICATION_USER_PREFERENCE);
    }

    /**
     * Create a detached, initialised NotificationUserPreferenceRecord
     */
    public NotificationUserPreferenceRecord(ULong id, ULong appId, ULong userId, String code, JSON preferences, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(NotificationUserPreference.NOTIFICATION_USER_PREFERENCE);

        setId(id);
        setAppId(appId);
        setUserId(userId);
        setCode(code);
        setPreferences(preferences);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
