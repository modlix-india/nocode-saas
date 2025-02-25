/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.enums.SecurityAppRegUserDesignationLevel;
import com.fincity.security.jooq.tables.SecurityAppRegUserDesignation;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppRegUserDesignationRecord extends UpdatableRecordImpl<SecurityAppRegUserDesignationRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_reg_user_designation.ID</code>.
     * Primary key
     */
    public SecurityAppRegUserDesignationRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_designation.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.CLIENT_ID</code>. Client
     * ID
     */
    public SecurityAppRegUserDesignationRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.CLIENT_ID</code>. Client
     * ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.CLIENT_TYPE</code>.
     * Client type
     */
    public SecurityAppRegUserDesignationRecord setClientType(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.CLIENT_TYPE</code>.
     * Client type
     */
    public String getClientType() {
        return (String) get(2);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.APP_ID</code>. App ID
     */
    public SecurityAppRegUserDesignationRecord setAppId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.APP_ID</code>. App ID
     */
    public ULong getAppId() {
        return (ULong) get(3);
    }

    /**
     * Setter for <code>security.security_app_reg_user_designation.LEVEL</code>.
     * Access level
     */
    public SecurityAppRegUserDesignationRecord setLevel(SecurityAppRegUserDesignationLevel value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_designation.LEVEL</code>.
     * Access level
     */
    public SecurityAppRegUserDesignationLevel getLevel() {
        return (SecurityAppRegUserDesignationLevel) get(4);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.BUSINESS_TYPE</code>.
     * Business type
     */
    public SecurityAppRegUserDesignationRecord setBusinessType(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.BUSINESS_TYPE</code>.
     * Business type
     */
    public String getBusinessType() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.DESIGNATION_ID</code>.
     * Designation ID
     */
    public SecurityAppRegUserDesignationRecord setDesignationId(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.DESIGNATION_ID</code>.
     * Designation ID
     */
    public ULong getDesignationId() {
        return (ULong) get(6);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public SecurityAppRegUserDesignationRecord setCreatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.CREATED_AT</code>. Time
     * when this row is created
     */
    public SecurityAppRegUserDesignationRecord setCreatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.CREATED_AT</code>. Time
     * when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(8);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public SecurityAppRegUserDesignationRecord setUpdatedBy(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(9);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_designation.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public SecurityAppRegUserDesignationRecord setUpdatedAt(LocalDateTime value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_designation.UPDATED_AT</code>. Time
     * when this row is updated
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
     * Create a detached SecurityAppRegUserDesignationRecord
     */
    public SecurityAppRegUserDesignationRecord() {
        super(SecurityAppRegUserDesignation.SECURITY_APP_REG_USER_DESIGNATION);
    }

    /**
     * Create a detached, initialised SecurityAppRegUserDesignationRecord
     */
    public SecurityAppRegUserDesignationRecord(ULong id, ULong clientId, String clientType, ULong appId, SecurityAppRegUserDesignationLevel level, String businessType, ULong designationId, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityAppRegUserDesignation.SECURITY_APP_REG_USER_DESIGNATION);

        setId(id);
        setClientId(clientId);
        setClientType(clientType);
        setAppId(appId);
        setLevel(level);
        setBusinessType(businessType);
        setDesignationId(designationId);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
