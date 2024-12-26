/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityClientUrl;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityClientUrlRecord extends UpdatableRecordImpl<SecurityClientUrlRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_client_url.ID</code>. Primary key
     */
    public SecurityClientUrlRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_client_url.CLIENT_ID</code>. Client ID
     */
    public SecurityClientUrlRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.CLIENT_ID</code>. Client ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_client_url.URL_PATTERN</code>. URL
     * Pattern to identify users Client ID
     */
    public SecurityClientUrlRecord setUrlPattern(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.URL_PATTERN</code>. URL
     * Pattern to identify users Client ID
     */
    public String getUrlPattern() {
        return (String) get(2);
    }

    /**
     * Setter for <code>security.security_client_url.APP_CODE</code>.
     */
    public SecurityClientUrlRecord setAppCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.APP_CODE</code>.
     */
    public String getAppCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>security.security_client_url.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public SecurityClientUrlRecord setCreatedBy(ULong value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(4);
    }

    /**
     * Setter for <code>security.security_client_url.CREATED_AT</code>. Time
     * when this row is created
     */
    public SecurityClientUrlRecord setCreatedAt(LocalDateTime value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.CREATED_AT</code>. Time
     * when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(5);
    }

    /**
     * Setter for <code>security.security_client_url.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public SecurityClientUrlRecord setUpdatedBy(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(6);
    }

    /**
     * Setter for <code>security.security_client_url.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public SecurityClientUrlRecord setUpdatedAt(LocalDateTime value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_url.UPDATED_AT</code>. Time
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
     * Create a detached SecurityClientUrlRecord
     */
    public SecurityClientUrlRecord() {
        super(SecurityClientUrl.SECURITY_CLIENT_URL);
    }

    /**
     * Create a detached, initialised SecurityClientUrlRecord
     */
    public SecurityClientUrlRecord(ULong id, ULong clientId, String urlPattern, String appCode, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityClientUrl.SECURITY_CLIENT_URL);

        setId(id);
        setClientId(clientId);
        setUrlPattern(urlPattern);
        setAppCode(appCode);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
