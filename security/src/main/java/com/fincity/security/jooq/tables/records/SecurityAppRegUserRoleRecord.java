/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.enums.SecurityAppRegUserRoleLevel;
import com.fincity.security.jooq.tables.SecurityAppRegUserRole;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppRegUserRoleRecord extends UpdatableRecordImpl<SecurityAppRegUserRoleRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_reg_user_role.ID</code>. Primary
     * key
     */
    public SecurityAppRegUserRoleRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.ID</code>. Primary
     * key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.CLIENT_ID</code>.
     * Client ID
     */
    public SecurityAppRegUserRoleRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.CLIENT_ID</code>.
     * Client ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.CLIENT_TYPE</code>.
     * Client type
     */
    public SecurityAppRegUserRoleRecord setClientType(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.CLIENT_TYPE</code>.
     * Client type
     */
    public String getClientType() {
        return (String) get(2);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.APP_ID</code>. App
     * ID
     */
    public SecurityAppRegUserRoleRecord setAppId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.APP_ID</code>. App
     * ID
     */
    public ULong getAppId() {
        return (ULong) get(3);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.ROLE_ID</code>. Role
     * ID
     */
    public SecurityAppRegUserRoleRecord setRoleId(ULong value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.ROLE_ID</code>. Role
     * ID
     */
    public ULong getRoleId() {
        return (ULong) get(4);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.LEVEL</code>. Access
     * level
     */
    public SecurityAppRegUserRoleRecord setLevel(SecurityAppRegUserRoleLevel value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.LEVEL</code>. Access
     * level
     */
    public SecurityAppRegUserRoleLevel getLevel() {
        return (SecurityAppRegUserRoleLevel) get(5);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_user_role.BUSINESS_TYPE</code>. Business
     * type
     */
    public SecurityAppRegUserRoleRecord setBusinessType(String value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_user_role.BUSINESS_TYPE</code>. Business
     * type
     */
    public String getBusinessType() {
        return (String) get(6);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public SecurityAppRegUserRoleRecord setCreatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for <code>security.security_app_reg_user_role.CREATED_AT</code>.
     * Time when this row is created
     */
    public SecurityAppRegUserRoleRecord setCreatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_user_role.CREATED_AT</code>.
     * Time when this row is created
     */
    public LocalDateTime getCreatedAt() {
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
     * Create a detached SecurityAppRegUserRoleRecord
     */
    public SecurityAppRegUserRoleRecord() {
        super(SecurityAppRegUserRole.SECURITY_APP_REG_USER_ROLE);
    }

    /**
     * Create a detached, initialised SecurityAppRegUserRoleRecord
     */
    public SecurityAppRegUserRoleRecord(ULong id, ULong clientId, String clientType, ULong appId, ULong roleId, SecurityAppRegUserRoleLevel level, String businessType, ULong createdBy, LocalDateTime createdAt) {
        super(SecurityAppRegUserRole.SECURITY_APP_REG_USER_ROLE);

        setId(id);
        setClientId(clientId);
        setClientType(clientType);
        setAppId(appId);
        setRoleId(roleId);
        setLevel(level);
        setBusinessType(businessType);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        resetChangedOnNotNull();
    }
}
