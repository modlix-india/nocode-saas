/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq;


import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityClient;
import com.fincity.security.jooq.tables.SecurityClientManage;
import com.fincity.security.jooq.tables.SecurityClientPackage;
import com.fincity.security.jooq.tables.SecurityClientPasswordPolicy;
import com.fincity.security.jooq.tables.SecurityClientType;
import com.fincity.security.jooq.tables.SecurityClientUrl;
import com.fincity.security.jooq.tables.SecurityOrgStructure;
import com.fincity.security.jooq.tables.SecurityPackage;
import com.fincity.security.jooq.tables.SecurityPackageRole;
import com.fincity.security.jooq.tables.SecurityPastPasswords;
import com.fincity.security.jooq.tables.SecurityPermission;
import com.fincity.security.jooq.tables.SecurityRole;
import com.fincity.security.jooq.tables.SecurityRolePermission;
import com.fincity.security.jooq.tables.SecuritySoxLog;
import com.fincity.security.jooq.tables.SecurityUser;
import com.fincity.security.jooq.tables.SecurityUserRolePermission;
import com.fincity.security.jooq.tables.SecurityUserToken;

import java.util.Arrays;
import java.util.List;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Security extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security</code>
     */
    public static final Security SECURITY = new Security();

    /**
     * The table <code>security.security_app</code>.
     */
    public final SecurityApp SECURITY_APP = SecurityApp.SECURITY_APP;

    /**
     * The table <code>security.security_client</code>.
     */
    public final SecurityClient SECURITY_CLIENT = SecurityClient.SECURITY_CLIENT;

    /**
     * The table <code>security.security_client_manage</code>.
     */
    public final SecurityClientManage SECURITY_CLIENT_MANAGE = SecurityClientManage.SECURITY_CLIENT_MANAGE;

    /**
     * The table <code>security.security_client_package</code>.
     */
    public final SecurityClientPackage SECURITY_CLIENT_PACKAGE = SecurityClientPackage.SECURITY_CLIENT_PACKAGE;

    /**
     * The table <code>security.security_client_password_policy</code>.
     */
    public final SecurityClientPasswordPolicy SECURITY_CLIENT_PASSWORD_POLICY = SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

    /**
     * The table <code>security.security_client_type</code>.
     */
    public final SecurityClientType SECURITY_CLIENT_TYPE = SecurityClientType.SECURITY_CLIENT_TYPE;

    /**
     * The table <code>security.security_client_url</code>.
     */
    public final SecurityClientUrl SECURITY_CLIENT_URL = SecurityClientUrl.SECURITY_CLIENT_URL;

    /**
     * The table <code>security.security_org_structure</code>.
     */
    public final SecurityOrgStructure SECURITY_ORG_STRUCTURE = SecurityOrgStructure.SECURITY_ORG_STRUCTURE;

    /**
     * The table <code>security.security_package</code>.
     */
    public final SecurityPackage SECURITY_PACKAGE = SecurityPackage.SECURITY_PACKAGE;

    /**
     * The table <code>security.security_package_role</code>.
     */
    public final SecurityPackageRole SECURITY_PACKAGE_ROLE = SecurityPackageRole.SECURITY_PACKAGE_ROLE;

    /**
     * The table <code>security.security_past_passwords</code>.
     */
    public final SecurityPastPasswords SECURITY_PAST_PASSWORDS = SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

    /**
     * The table <code>security.security_permission</code>.
     */
    public final SecurityPermission SECURITY_PERMISSION = SecurityPermission.SECURITY_PERMISSION;

    /**
     * The table <code>security.security_role</code>.
     */
    public final SecurityRole SECURITY_ROLE = SecurityRole.SECURITY_ROLE;

    /**
     * The table <code>security.security_role_permission</code>.
     */
    public final SecurityRolePermission SECURITY_ROLE_PERMISSION = SecurityRolePermission.SECURITY_ROLE_PERMISSION;

    /**
     * The table <code>security.security_sox_log</code>.
     */
    public final SecuritySoxLog SECURITY_SOX_LOG = SecuritySoxLog.SECURITY_SOX_LOG;

    /**
     * The table <code>security.security_user</code>.
     */
    public final SecurityUser SECURITY_USER = SecurityUser.SECURITY_USER;

    /**
     * The table <code>security.security_user_role_permission</code>.
     */
    public final SecurityUserRolePermission SECURITY_USER_ROLE_PERMISSION = SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

    /**
     * The table <code>security.security_user_token</code>.
     */
    public final SecurityUserToken SECURITY_USER_TOKEN = SecurityUserToken.SECURITY_USER_TOKEN;

    /**
     * No further instances allowed
     */
    private Security() {
        super("security", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.asList(
            SecurityApp.SECURITY_APP,
            SecurityClient.SECURITY_CLIENT,
            SecurityClientManage.SECURITY_CLIENT_MANAGE,
            SecurityClientPackage.SECURITY_CLIENT_PACKAGE,
            SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY,
            SecurityClientType.SECURITY_CLIENT_TYPE,
            SecurityClientUrl.SECURITY_CLIENT_URL,
            SecurityOrgStructure.SECURITY_ORG_STRUCTURE,
            SecurityPackage.SECURITY_PACKAGE,
            SecurityPackageRole.SECURITY_PACKAGE_ROLE,
            SecurityPastPasswords.SECURITY_PAST_PASSWORDS,
            SecurityPermission.SECURITY_PERMISSION,
            SecurityRole.SECURITY_ROLE,
            SecurityRolePermission.SECURITY_ROLE_PERMISSION,
            SecuritySoxLog.SECURITY_SOX_LOG,
            SecurityUser.SECURITY_USER,
            SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION,
            SecurityUserToken.SECURITY_USER_TOKEN
        );
    }
}
