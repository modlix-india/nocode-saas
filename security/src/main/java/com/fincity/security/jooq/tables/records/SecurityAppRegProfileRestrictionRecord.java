/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.enums.SecurityAppRegProfileRestrictionLevel;
import com.fincity.security.jooq.tables.SecurityAppRegProfileRestriction;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppRegProfileRestrictionRecord extends UpdatableRecordImpl<SecurityAppRegProfileRestrictionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_reg_profile_restriction.ID</code>.
     * Primary key
     */
    public SecurityAppRegProfileRestrictionRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_profile_restriction.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.CLIENT_ID</code>.
     * Client ID
     */
    public SecurityAppRegProfileRestrictionRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.CLIENT_ID</code>.
     * Client ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.CLIENT_TYPE</code>.
     * Client type
     */
    public SecurityAppRegProfileRestrictionRecord setClientType(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.CLIENT_TYPE</code>.
     * Client type
     */
    public String getClientType() {
        return (String) get(2);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.APP_ID</code>. App ID
     */
    public SecurityAppRegProfileRestrictionRecord setAppId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.APP_ID</code>. App ID
     */
    public ULong getAppId() {
        return (ULong) get(3);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.LEVEL</code>. Access
     * level
     */
    public SecurityAppRegProfileRestrictionRecord setLevel(SecurityAppRegProfileRestrictionLevel value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.LEVEL</code>. Access
     * level
     */
    public SecurityAppRegProfileRestrictionLevel getLevel() {
        return (SecurityAppRegProfileRestrictionLevel) get(4);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.BUSINESS_TYPE</code>.
     * Business type
     */
    public SecurityAppRegProfileRestrictionRecord setBusinessType(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.BUSINESS_TYPE</code>.
     * Business type
     */
    public String getBusinessType() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.PROFILE_ID</code>.
     * Profile ID
     */
    public SecurityAppRegProfileRestrictionRecord setProfileId(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.PROFILE_ID</code>.
     * Profile ID
     */
    public ULong getProfileId() {
        return (ULong) get(6);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public SecurityAppRegProfileRestrictionRecord setCreatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_profile_restriction.CREATED_AT</code>.
     * Time when this row is created
     */
    public SecurityAppRegProfileRestrictionRecord setCreatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_profile_restriction.CREATED_AT</code>.
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
     * Create a detached SecurityAppRegProfileRestrictionRecord
     */
    public SecurityAppRegProfileRestrictionRecord() {
        super(SecurityAppRegProfileRestriction.SECURITY_APP_REG_PROFILE_RESTRICTION);
    }

    /**
     * Create a detached, initialised SecurityAppRegProfileRestrictionRecord
     */
    public SecurityAppRegProfileRestrictionRecord(ULong id, ULong clientId, String clientType, ULong appId, SecurityAppRegProfileRestrictionLevel level, String businessType, ULong profileId, ULong createdBy, LocalDateTime createdAt) {
        super(SecurityAppRegProfileRestriction.SECURITY_APP_REG_PROFILE_RESTRICTION);

        setId(id);
        setClientId(clientId);
        setClientType(clientType);
        setAppId(appId);
        setLevel(level);
        setBusinessType(businessType);
        setProfileId(profileId);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        resetChangedOnNotNull();
    }
}
