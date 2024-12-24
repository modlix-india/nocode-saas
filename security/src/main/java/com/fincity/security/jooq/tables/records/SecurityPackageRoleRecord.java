/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityPackageRole;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityPackageRoleRecord extends UpdatableRecordImpl<SecurityPackageRoleRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_package_role.ID</code>. Primary key
     */
    public SecurityPackageRoleRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package_role.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_package_role.PACKAGE_ID</code>.
     * Package ID
     */
    public SecurityPackageRoleRecord setPackageId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package_role.PACKAGE_ID</code>.
     * Package ID
     */
    public ULong getPackageId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_package_role.ROLE_ID</code>. Role ID
     */
    public SecurityPackageRoleRecord setRoleId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package_role.ROLE_ID</code>. Role ID
     */
    public ULong getRoleId() {
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
     * Create a detached SecurityPackageRoleRecord
     */
    public SecurityPackageRoleRecord() {
        super(SecurityPackageRole.SECURITY_PACKAGE_ROLE);
    }

    /**
     * Create a detached, initialised SecurityPackageRoleRecord
     */
    public SecurityPackageRoleRecord(ULong id, ULong packageId, ULong roleId) {
        super(SecurityPackageRole.SECURITY_PACKAGE_ROLE);

        setId(id);
        setPackageId(packageId);
        setRoleId(roleId);
        resetChangedOnNotNull();
    }
}
