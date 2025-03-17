/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityProfileClientRestriction;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityProfileClientRestrictionRecord extends UpdatableRecordImpl<SecurityProfileClientRestrictionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_profile_client_restriction.ID</code>.
     * Primary key
     */
    public SecurityProfileClientRestrictionRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_client_restriction.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>security.security_profile_client_restriction.PROFILE_ID</code>.
     * Profile ID for which this restriction belongs to
     */
    public SecurityProfileClientRestrictionRecord setProfileId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_profile_client_restriction.PROFILE_ID</code>.
     * Profile ID for which this restriction belongs to
     */
    public ULong getProfileId() {
        return (ULong) get(1);
    }

    /**
     * Setter for
     * <code>security.security_profile_client_restriction.APP_ID</code>. App ID
     * for which this restriction belongs to
     */
    public SecurityProfileClientRestrictionRecord setAppId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_profile_client_restriction.APP_ID</code>. App ID
     * for which this restriction belongs to
     */
    public ULong getAppId() {
        return (ULong) get(2);
    }

    /**
     * Setter for
     * <code>security.security_profile_client_restriction.CLIENT_ID</code>.
     * Client ID for which this restriction belongs to
     */
    public SecurityProfileClientRestrictionRecord setClientId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_profile_client_restriction.CLIENT_ID</code>.
     * Client ID for which this restriction belongs to
     */
    public ULong getClientId() {
        return (ULong) get(3);
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
     * Create a detached SecurityProfileClientRestrictionRecord
     */
    public SecurityProfileClientRestrictionRecord() {
        super(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION);
    }

    /**
     * Create a detached, initialised SecurityProfileClientRestrictionRecord
     */
    public SecurityProfileClientRestrictionRecord(ULong id, ULong profileId, ULong appId, ULong clientId) {
        super(SecurityProfileClientRestriction.SECURITY_PROFILE_CLIENT_RESTRICTION);

        setId(id);
        setProfileId(profileId);
        setAppId(appId);
        setClientId(clientId);
        resetChangedOnNotNull();
    }
}
