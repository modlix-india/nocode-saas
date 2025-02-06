/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq.tables.records;


import com.fincity.saas.notification.jooq.tables.NotificationConnection;

import java.time.LocalDateTime;

import org.jooq.JSON;
import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class NotificationConnectionRecord extends UpdatableRecordImpl<NotificationConnectionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>notification.notification_connection.ID</code>. Primary
     * key
     */
    public NotificationConnectionRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.ID</code>. Primary
     * key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>notification.notification_connection.CLIENT_ID</code>.
     * Identifier for the client to which this OTP policy belongs. References
     * security_client table
     */
    public NotificationConnectionRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.CLIENT_ID</code>.
     * Identifier for the client to which this OTP policy belongs. References
     * security_client table
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>notification.notification_connection.APP_ID</code>.
     * Identifier for the application to which this OTP policy belongs.
     * References security_app table
     */
    public NotificationConnectionRecord setAppId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.APP_ID</code>.
     * Identifier for the application to which this OTP policy belongs.
     * References security_app table
     */
    public ULong getAppId() {
        return (ULong) get(2);
    }

    /**
     * Setter for <code>notification.notification_connection.CODE</code>. Code
     */
    public NotificationConnectionRecord setCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.CODE</code>. Code
     */
    public String getCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>notification.notification_connection.NAME</code>.
     * Connection name
     */
    public NotificationConnectionRecord setName(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.NAME</code>.
     * Connection name
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for <code>notification.notification_connection.DESCRIPTION</code>.
     * Description of notification connection
     */
    public NotificationConnectionRecord setDescription(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.DESCRIPTION</code>.
     * Description of notification connection
     */
    public String getDescription() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>notification.notification_connection.CONNECTION_DETAILS</code>.
     * Connection details object
     */
    public NotificationConnectionRecord setConnectionDetails(JSON value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>notification.notification_connection.CONNECTION_DETAILS</code>.
     * Connection details object
     */
    public JSON getConnectionDetails() {
        return (JSON) get(6);
    }

    /**
     * Setter for <code>notification.notification_connection.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public NotificationConnectionRecord setCreatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for <code>notification.notification_connection.CREATED_AT</code>.
     * Time when this row is created
     */
    public NotificationConnectionRecord setCreatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.CREATED_AT</code>.
     * Time when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(8);
    }

    /**
     * Setter for <code>notification.notification_connection.UPDATED_BY</code>.
     * ID of the user who updated this row
     */
    public NotificationConnectionRecord setUpdatedBy(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.UPDATED_BY</code>.
     * ID of the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(9);
    }

    /**
     * Setter for <code>notification.notification_connection.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public NotificationConnectionRecord setUpdatedAt(LocalDateTime value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for <code>notification.notification_connection.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(10);
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
     * Create a detached NotificationConnectionRecord
     */
    public NotificationConnectionRecord() {
        super(NotificationConnection.NOTIFICATION_CONNECTION);
    }

    /**
     * Create a detached, initialised NotificationConnectionRecord
     */
    public NotificationConnectionRecord(ULong id, ULong clientId, ULong appId, String code, String name, String description, JSON connectionDetails, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(NotificationConnection.NOTIFICATION_CONNECTION);

        setId(id);
        setClientId(clientId);
        setAppId(appId);
        setCode(code);
        setName(name);
        setDescription(description);
        setConnectionDetails(connectionDetails);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
