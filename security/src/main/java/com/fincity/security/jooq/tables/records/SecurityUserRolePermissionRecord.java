/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityUserRolePermission;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityUserRolePermissionRecord extends UpdatableRecordImpl<SecurityUserRolePermissionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_user_role_permission.ID</code>.
     * Primary key
     */
    public SecurityUserRolePermissionRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_user_role_permission.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_user_role_permission.USER_ID</code>.
     * User ID
     */
    public SecurityUserRolePermissionRecord setUserId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_user_role_permission.USER_ID</code>.
     * User ID
     */
    public ULong getUserId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_user_role_permission.ROLE_ID</code>.
     * Role ID
     */
    public SecurityUserRolePermissionRecord setRoleId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_user_role_permission.ROLE_ID</code>.
     * Role ID
     */
    public ULong getRoleId() {
        return (ULong) get(2);
    }

    /**
     * Setter for
     * <code>security.security_user_role_permission.PERMISSION_ID</code>.
     * Permission ID
     */
    public SecurityUserRolePermissionRecord setPermissionId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_user_role_permission.PERMISSION_ID</code>.
     * Permission ID
     */
    public ULong getPermissionId() {
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
     * Create a detached SecurityUserRolePermissionRecord
     */
    public SecurityUserRolePermissionRecord() {
        super(SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION);
    }

    /**
     * Create a detached, initialised SecurityUserRolePermissionRecord
     */
    public SecurityUserRolePermissionRecord(ULong id, ULong userId, ULong roleId, ULong permissionId) {
        super(SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION);

        setId(id);
        setUserId(userId);
        setRoleId(roleId);
        setPermissionId(permissionId);
        resetChangedOnNotNull();
    }
}
