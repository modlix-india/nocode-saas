/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityUserAddress;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityUserAddressRecord extends UpdatableRecordImpl<SecurityUserAddressRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_user_address.ID</code>. Primary key
     */
    public SecurityUserAddressRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_user_address.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_user_address.USER_ID</code>. User ID
     */
    public SecurityUserAddressRecord setUserId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_user_address.USER_ID</code>. User ID
     */
    public ULong getUserId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_user_address.ADDRESS_ID</code>.
     * Address ID
     */
    public SecurityUserAddressRecord setAddressId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_user_address.ADDRESS_ID</code>.
     * Address ID
     */
    public ULong getAddressId() {
        return (ULong) get(2);
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
     * Create a detached SecurityUserAddressRecord
     */
    public SecurityUserAddressRecord() {
        super(SecurityUserAddress.SECURITY_USER_ADDRESS);
    }

    /**
     * Create a detached, initialised SecurityUserAddressRecord
     */
    public SecurityUserAddressRecord(ULong id, ULong userId, ULong addressId) {
        super(SecurityUserAddress.SECURITY_USER_ADDRESS);

        setId(id);
        setUserId(userId);
        setAddressId(addressId);
        resetChangedOnNotNull();
    }
}
