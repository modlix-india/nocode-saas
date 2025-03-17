/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityProfileArrangement;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityProfileArrangementRecord extends UpdatableRecordImpl<SecurityProfileArrangementRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_profile_arrangement.ID</code>. Primary
     * key
     */
    public SecurityProfileArrangementRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.ID</code>. Primary
     * key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.CLIENT_ID</code>.
     * Client ID for which this arrangement belongs to
     */
    public SecurityProfileArrangementRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.CLIENT_ID</code>.
     * Client ID for which this arrangement belongs to
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.PROFILE_ID</code>.
     * Profile ID for which this arrangement belongs to
     */
    public SecurityProfileArrangementRecord setProfileId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.PROFILE_ID</code>.
     * Profile ID for which this arrangement belongs to
     */
    public ULong getProfileId() {
        return (ULong) get(2);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.ROLE_ID</code>.
     * Role ID for which this arrangement belongs to
     */
    public SecurityProfileArrangementRecord setRoleId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.ROLE_ID</code>.
     * Role ID for which this arrangement belongs to
     */
    public ULong getRoleId() {
        return (ULong) get(3);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.NAME</code>. Name
     * of the arrangement
     */
    public SecurityProfileArrangementRecord setName(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.NAME</code>. Name
     * of the arrangement
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.SHORT_NAME</code>.
     * Short name of the arrangement
     */
    public SecurityProfileArrangementRecord setShortName(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.SHORT_NAME</code>.
     * Short name of the arrangement
     */
    public String getShortName() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>security.security_profile_arrangement.DESCRIPTION</code>.
     * Description of the arrangement
     */
    public SecurityProfileArrangementRecord setDescription(String value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_profile_arrangement.DESCRIPTION</code>.
     * Description of the arrangement
     */
    public String getDescription() {
        return (String) get(6);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.ASSIGNABLE</code>.
     * Whether the arrangement is assignable
     */
    public SecurityProfileArrangementRecord setAssignable(Byte value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.ASSIGNABLE</code>.
     * Whether the arrangement is assignable
     */
    public Byte getAssignable() {
        return (Byte) get(7);
    }

    /**
     * Setter for <code>security.security_profile_arrangement.ORDER</code>.
     * Order of the arrangement
     */
    public SecurityProfileArrangementRecord setOrder(Integer value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>security.security_profile_arrangement.ORDER</code>.
     * Order of the arrangement
     */
    public Integer getOrder() {
        return (Integer) get(8);
    }

    /**
     * Setter for
     * <code>security.security_profile_arrangement.PARENT_ARRANGEMENT_ID</code>.
     * Parent arrangement ID for hierarchical structure
     */
    public SecurityProfileArrangementRecord setParentArrangementId(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_profile_arrangement.PARENT_ARRANGEMENT_ID</code>.
     * Parent arrangement ID for hierarchical structure
     */
    public ULong getParentArrangementId() {
        return (ULong) get(9);
    }

    /**
     * Setter for
     * <code>security.security_profile_arrangement.OVERRIDE_ARRANGEMENT_ID</code>.
     * Override arrangement ID for which this arrangement belongs to
     */
    public SecurityProfileArrangementRecord setOverrideArrangementId(ULong value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_profile_arrangement.OVERRIDE_ARRANGEMENT_ID</code>.
     * Override arrangement ID for which this arrangement belongs to
     */
    public ULong getOverrideArrangementId() {
        return (ULong) get(10);
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
     * Create a detached SecurityProfileArrangementRecord
     */
    public SecurityProfileArrangementRecord() {
        super(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT);
    }

    /**
     * Create a detached, initialised SecurityProfileArrangementRecord
     */
    public SecurityProfileArrangementRecord(ULong id, ULong clientId, ULong profileId, ULong roleId, String name, String shortName, String description, Byte assignable, Integer order, ULong parentArrangementId, ULong overrideArrangementId) {
        super(SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT);

        setId(id);
        setClientId(clientId);
        setProfileId(profileId);
        setRoleId(roleId);
        setName(name);
        setShortName(shortName);
        setDescription(description);
        setAssignable(assignable);
        setOrder(order);
        setParentArrangementId(parentArrangementId);
        setOverrideArrangementId(overrideArrangementId);
        resetChangedOnNotNull();
    }
}
