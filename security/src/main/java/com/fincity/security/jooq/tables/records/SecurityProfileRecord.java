/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityProfile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityProfileRecord extends UpdatableRecordImpl<SecurityProfileRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_profile.ID</code>. Primary key
     */
    public SecurityProfileRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_profile.CLIENT_ID</code>. Client ID
     * for which this profile belongs to
     */
    public SecurityProfileRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.CLIENT_ID</code>. Client ID
     * for which this profile belongs to
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_profile.NAME</code>. Name of the
     * profile
     */
    public SecurityProfileRecord setName(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.NAME</code>. Name of the
     * profile
     */
    public String getName() {
        return (String) get(2);
    }

    /**
     * Setter for <code>security.security_profile.APP_ID</code>.
     */
    public SecurityProfileRecord setAppId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.APP_ID</code>.
     */
    public ULong getAppId() {
        return (ULong) get(3);
    }

    /**
     * Setter for <code>security.security_profile.DESCRIPTION</code>.
     * Description of the profile
     */
    public SecurityProfileRecord setDescription(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.DESCRIPTION</code>.
     * Description of the profile
     */
    public String getDescription() {
        return (String) get(4);
    }

    /**
     * Setter for <code>security.security_profile.ROOT_PROFILE_ID</code>.
     * Profile ID to which the user is assigned
     */
    public SecurityProfileRecord setRootProfileId(ULong value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.ROOT_PROFILE_ID</code>.
     * Profile ID to which the user is assigned
     */
    public ULong getRootProfileId() {
        return (ULong) get(5);
    }

    /**
     * Setter for <code>security.security_profile.ARRANGEMENT</code>.
     * Arrangement of the profile
     */
    public SecurityProfileRecord setArrangement(LinkedHashMap value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.ARRANGEMENT</code>.
     * Arrangement of the profile
     */
    public LinkedHashMap getArrangement() {
        return (LinkedHashMap) get(6);
    }

    /**
     * Setter for <code>security.security_profile.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public SecurityProfileRecord setCreatedBy(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(7);
    }

    /**
     * Setter for <code>security.security_profile.CREATED_AT</code>. Time when
     * this row is created
     */
    public SecurityProfileRecord setCreatedAt(LocalDateTime value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.CREATED_AT</code>. Time when
     * this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(8);
    }

    /**
     * Setter for <code>security.security_profile.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public SecurityProfileRecord setUpdatedBy(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(9);
    }

    /**
     * Setter for <code>security.security_profile.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public SecurityProfileRecord setUpdatedAt(LocalDateTime value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile.UPDATED_AT</code>. Time when
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
     * Create a detached SecurityProfileRecord
     */
    public SecurityProfileRecord() {
        super(SecurityProfile.SECURITY_PROFILE);
    }

    /**
     * Create a detached, initialised SecurityProfileRecord
     */
    public SecurityProfileRecord(ULong id, ULong clientId, String name, ULong appId, String description, ULong rootProfileId, LinkedHashMap arrangement, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityProfile.SECURITY_PROFILE);

        setId(id);
        setClientId(clientId);
        setName(name);
        setAppId(appId);
        setDescription(description);
        setRootProfileId(rootProfileId);
        setArrangement(arrangement);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
