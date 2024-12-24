/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityClientPackage;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityClientPackageRecord extends UpdatableRecordImpl<SecurityClientPackageRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_client_package.ID</code>. Primary key
     */
    public SecurityClientPackageRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_package.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_client_package.CLIENT_ID</code>.
     * Client ID
     */
    public SecurityClientPackageRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_package.CLIENT_ID</code>.
     * Client ID
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_client_package.PACKAGE_ID</code>.
     * Package ID
     */
    public SecurityClientPackageRecord setPackageId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_client_package.PACKAGE_ID</code>.
     * Package ID
     */
    public ULong getPackageId() {
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
     * Create a detached SecurityClientPackageRecord
     */
    public SecurityClientPackageRecord() {
        super(SecurityClientPackage.SECURITY_CLIENT_PACKAGE);
    }

    /**
     * Create a detached, initialised SecurityClientPackageRecord
     */
    public SecurityClientPackageRecord(ULong id, ULong clientId, ULong packageId) {
        super(SecurityClientPackage.SECURITY_CLIENT_PACKAGE);

        setId(id);
        setClientId(clientId);
        setPackageId(packageId);
        resetChangedOnNotNull();
    }
}
