/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables.records;


import com.fincity.saas.notification.jooq.tables.NotificationUserPreferences;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationUserPreferencesRecord extends UpdatableRecordImpl<NotificationUserPreferencesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>notification.notification_user_preferences.ID</code>.
     * Primary key
     */
    public NotificationUserPreferencesRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_user_preferences.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>notification.notification_user_preferences.CODE</code>.
     * Unique Code to identify this row
     */
    public NotificationUserPreferencesRecord setCode(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_user_preferences.CODE</code>.
     * Unique Code to identify this row
     */
    public String getCode() {
        return (String) get(1);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.APP_ID</code>. App Id
     * for which this user preference is getting created. References
     * security_app table
     */
    public NotificationUserPreferencesRecord setAppId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.APP_ID</code>. App Id
     * for which this user preference is getting created. References
     * security_app table
     */
    public ULong getAppId() {
        return (ULong) get(2);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.USER_ID</code>. App User
     * Id under which this user preference is getting created. References
     * security_user table
     */
    public NotificationUserPreferencesRecord setUserId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.USER_ID</code>. App User
     * Id under which this user preference is getting created. References
     * security_user table
     */
    public ULong getUserId() {
        return (ULong) get(3);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.PREFERENCES</code>.
     * Notification preference
     */
    public NotificationUserPreferencesRecord setPreferences(Map value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.PREFERENCES</code>.
     * Notification preference
     */
    public Map getPreferences() {
        return (Map) get(4);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.ENABLED</code>.
     * Notification enabled or not
     */
    public NotificationUserPreferencesRecord setEnabled(Byte value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.ENABLED</code>.
     * Notification enabled or not
     */
    public Byte getEnabled() {
        return (Byte) get(5);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public NotificationUserPreferencesRecord setCreatedBy(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(6);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.CREATED_AT</code>. Time
     * when this row is created
     */
    public NotificationUserPreferencesRecord setCreatedAt(LocalDateTime value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.CREATED_AT</code>. Time
     * when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(7);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public NotificationUserPreferencesRecord setUpdatedBy(ULong value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(8);
    }

    /**
     * Setter for
     * <code>notification.notification_user_preferences.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public NotificationUserPreferencesRecord setUpdatedAt(LocalDateTime value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_user_preferences.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(9);
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
     * Create a detached NotificationUserPreferencesRecord
     */
    public NotificationUserPreferencesRecord() {
        super(NotificationUserPreferences.NOTIFICATION_USER_PREFERENCES);
    }

    /**
     * Create a detached, initialised NotificationUserPreferencesRecord
     */
    public NotificationUserPreferencesRecord(ULong id, String code, ULong appId, ULong userId, Map preferences, Byte enabled, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(NotificationUserPreferences.NOTIFICATION_USER_PREFERENCES);

        setId(id);
        setCode(code);
        setAppId(appId);
        setUserId(userId);
        setPreferences(preferences);
        setEnabled(enabled);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetTouchedOnNotNull();
    }
}
