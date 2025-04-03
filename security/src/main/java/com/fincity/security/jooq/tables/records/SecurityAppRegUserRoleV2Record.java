/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.enums.SecurityAppRegUserRoleV2Level;
import com.fincity.security.jooq.tables.SecurityAppRegUserRoleV2;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppRegUserRoleV2Record extends UpdatableRecordImpl<SecurityAppRegUserRoleV2Record> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_reg_user_role_v2.ID</code>.
     * Primary key
     */
    public SecurityAppRegUserRoleV2Record setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role_v2.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role_v2.CLIENT_ID</code>.
     * Client ID
     */
    public SecurityAppRegUserRoleV2Record setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role_v2.CLIENT_ID</code>.
     * Client ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role_v2.CLIENT_TYPE</code>. Client
     * type
     */
    public SecurityAppRegUserRoleV2Record setClientType(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role_v2.CLIENT_TYPE</code>. Client
     * type
     */
    public String getClientType() {
        return (String) get(2);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role_v2.APP_ID</code>.
     * App ID
     */
    public SecurityAppRegUserRoleV2Record setAppId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role_v2.APP_ID</code>.
     * App ID
     */
    public ULong getAppId() {
        return (ULong) get(3);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role_v2.LEVEL</code>.
     * Access level
     */
    public SecurityAppRegUserRoleV2Record setLevel(SecurityAppRegUserRoleV2Level value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role_v2.LEVEL</code>.
     * Access level
     */
    public SecurityAppRegUserRoleV2Level getLevel() {
        return (SecurityAppRegUserRoleV2Level) get(4);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role_v2.BUSINESS_TYPE</code>.
     * Business type
     */
    public SecurityAppRegUserRoleV2Record setBusinessType(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role_v2.BUSINESS_TYPE</code>.
     * Business type
     */
    public String getBusinessType() {
        return (String) get(5);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role_v2.ROLE_ID</code>.
     * Role ID
     */
    public SecurityAppRegUserRoleV2Record setRoleId(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role_v2.ROLE_ID</code>.
     * Role ID
     */
    public ULong getRoleId() {
        return (ULong) get(6);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role_v2.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public SecurityAppRegUserRoleV2Record setCreatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role_v2.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role_v2.CREATED_AT</code>. Time when
     * this row is created
     */
    public SecurityAppRegUserRoleV2Record setCreatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role_v2.CREATED_AT</code>. Time when
     * this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(8);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role_v2.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public SecurityAppRegUserRoleV2Record setUpdatedBy(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role_v2.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(9);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role_v2.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public SecurityAppRegUserRoleV2Record setUpdatedAt(LocalDateTime value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role_v2.UPDATED_AT</code>. Time when
     * this row is updated
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
     * Create a detached SecurityAppRegUserRoleV2Record
     */
    public SecurityAppRegUserRoleV2Record() {
        super(SecurityAppRegUserRoleV2.SECURITY_APP_REG_USER_ROLE_V2);
    }

    /**
     * Create a detached, initialised SecurityAppRegUserRoleV2Record
     */
    public SecurityAppRegUserRoleV2Record(ULong id, ULong clientId, String clientType, ULong appId, SecurityAppRegUserRoleV2Level level, String businessType, ULong roleId, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityAppRegUserRoleV2.SECURITY_APP_REG_USER_ROLE_V2);

        setId(id);
        setClientId(clientId);
        setClientType(clientType);
        setAppId(appId);
        setLevel(level);
        setBusinessType(businessType);
        setRoleId(roleId);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
