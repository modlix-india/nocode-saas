/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityAppAccess;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UByte;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppAccessRecord extends UpdatableRecordImpl<SecurityAppAccessRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_access.ID</code>. Primary key
     */
    public SecurityAppAccessRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_app_access.CLIENT_ID</code>. Client ID
     */
    public SecurityAppAccessRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.CLIENT_ID</code>. Client ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_app_access.APP_ID</code>. Application
     * ID
     */
    public SecurityAppAccessRecord setAppId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.APP_ID</code>. Application
     * ID
     */
    public ULong getAppId() {
        return (ULong) get(2);
    }

    /**
     * Setter for <code>security.security_app_access.EDIT_ACCESS</code>. Edit
     * access
     */
    public SecurityAppAccessRecord setEditAccess(UByte value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.EDIT_ACCESS</code>. Edit
     * access
     */
    public UByte getEditAccess() {
        return (UByte) get(3);
    }

    /**
     * Setter for <code>security.security_app_access.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public SecurityAppAccessRecord setCreatedBy(ULong value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(4);
    }

    /**
     * Setter for <code>security.security_app_access.CREATED_AT</code>. Time
     * when this row is created
     */
    public SecurityAppAccessRecord setCreatedAt(LocalDateTime value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.CREATED_AT</code>. Time
     * when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(5);
    }

    /**
     * Setter for <code>security.security_app_access.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public SecurityAppAccessRecord setUpdatedBy(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(6);
    }

    /**
     * Setter for <code>security.security_app_access.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public SecurityAppAccessRecord setUpdatedAt(LocalDateTime value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_access.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(7);
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
     * Create a detached SecurityAppAccessRecord
     */
    public SecurityAppAccessRecord() {
        super(SecurityAppAccess.SECURITY_APP_ACCESS);
    }

    /**
     * Create a detached, initialised SecurityAppAccessRecord
     */
    public SecurityAppAccessRecord(ULong id, ULong clientId, ULong appId, UByte editAccess, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityAppAccess.SECURITY_APP_ACCESS);

        setId(id);
        setClientId(clientId);
        setAppId(appId);
        setEditAccess(editAccess);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
