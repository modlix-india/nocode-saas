/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq;


import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityAppAccess;
import com.fincity.security.jooq.tables.SecurityAppPackage;
import com.fincity.security.jooq.tables.SecurityAppProperty;
import com.fincity.security.jooq.tables.SecurityAppUserRole;
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
import com.fincity.security.jooq.tables.SecuritySslCertificate;
import com.fincity.security.jooq.tables.SecuritySslChallenge;
import com.fincity.security.jooq.tables.SecuritySslRequest;
import com.fincity.security.jooq.tables.SecurityUser;
import com.fincity.security.jooq.tables.SecurityUserRolePermission;
import com.fincity.security.jooq.tables.SecurityUserToken;


/**
 * Convenience access to all tables in security.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Tables {

    /**
     * The table <code>security.security_app</code>.
     */
    public static final SecurityApp SECURITY_APP = SecurityApp.SECURITY_APP;

    /**
     * The table <code>security.security_app_access</code>.
     */
    public static final SecurityAppAccess SECURITY_APP_ACCESS = SecurityAppAccess.SECURITY_APP_ACCESS;

    /**
     * The table <code>security.security_app_package</code>.
     */
    public static final SecurityAppPackage SECURITY_APP_PACKAGE = SecurityAppPackage.SECURITY_APP_PACKAGE;

    /**
     * The table <code>security.security_app_property</code>.
     */
    public static final SecurityAppProperty SECURITY_APP_PROPERTY = SecurityAppProperty.SECURITY_APP_PROPERTY;

    /**
     * The table <code>security.security_app_user_role</code>.
     */
    public static final SecurityAppUserRole SECURITY_APP_USER_ROLE = SecurityAppUserRole.SECURITY_APP_USER_ROLE;

    /**
     * The table <code>security.security_client</code>.
     */
    public static final SecurityClient SECURITY_CLIENT = SecurityClient.SECURITY_CLIENT;

    /**
     * The table <code>security.security_client_manage</code>.
     */
    public static final SecurityClientManage SECURITY_CLIENT_MANAGE = SecurityClientManage.SECURITY_CLIENT_MANAGE;

    /**
     * The table <code>security.security_client_package</code>.
     */
    public static final SecurityClientPackage SECURITY_CLIENT_PACKAGE = SecurityClientPackage.SECURITY_CLIENT_PACKAGE;

    /**
     * The table <code>security.security_client_password_policy</code>.
     */
    public static final SecurityClientPasswordPolicy SECURITY_CLIENT_PASSWORD_POLICY = SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

    /**
     * The table <code>security.security_client_type</code>.
     */
    public static final SecurityClientType SECURITY_CLIENT_TYPE = SecurityClientType.SECURITY_CLIENT_TYPE;

    /**
     * The table <code>security.security_client_url</code>.
     */
    public static final SecurityClientUrl SECURITY_CLIENT_URL = SecurityClientUrl.SECURITY_CLIENT_URL;

    /**
     * The table <code>security.security_org_structure</code>.
     */
    public static final SecurityOrgStructure SECURITY_ORG_STRUCTURE = SecurityOrgStructure.SECURITY_ORG_STRUCTURE;

    /**
     * The table <code>security.security_package</code>.
     */
    public static final SecurityPackage SECURITY_PACKAGE = SecurityPackage.SECURITY_PACKAGE;

    /**
     * The table <code>security.security_package_role</code>.
     */
    public static final SecurityPackageRole SECURITY_PACKAGE_ROLE = SecurityPackageRole.SECURITY_PACKAGE_ROLE;

    /**
     * The table <code>security.security_past_passwords</code>.
     */
    public static final SecurityPastPasswords SECURITY_PAST_PASSWORDS = SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

    /**
     * The table <code>security.security_permission</code>.
     */
    public static final SecurityPermission SECURITY_PERMISSION = SecurityPermission.SECURITY_PERMISSION;

    /**
     * The table <code>security.security_role</code>.
     */
    public static final SecurityRole SECURITY_ROLE = SecurityRole.SECURITY_ROLE;

    /**
     * The table <code>security.security_role_permission</code>.
     */
    public static final SecurityRolePermission SECURITY_ROLE_PERMISSION = SecurityRolePermission.SECURITY_ROLE_PERMISSION;

    /**
     * The table <code>security.security_sox_log</code>.
     */
    public static final SecuritySoxLog SECURITY_SOX_LOG = SecuritySoxLog.SECURITY_SOX_LOG;

    /**
     * The table <code>security.security_ssl_certificate</code>.
     */
    public static final SecuritySslCertificate SECURITY_SSL_CERTIFICATE = SecuritySslCertificate.SECURITY_SSL_CERTIFICATE;

    /**
     * The table <code>security.security_ssl_challenge</code>.
     */
    public static final SecuritySslChallenge SECURITY_SSL_CHALLENGE = SecuritySslChallenge.SECURITY_SSL_CHALLENGE;

    /**
     * The table <code>security.security_ssl_request</code>.
     */
    public static final SecuritySslRequest SECURITY_SSL_REQUEST = SecuritySslRequest.SECURITY_SSL_REQUEST;

    /**
     * The table <code>security.security_user</code>.
     */
    public static final SecurityUser SECURITY_USER = SecurityUser.SECURITY_USER;

    /**
     * The table <code>security.security_user_role_permission</code>.
     */
    public static final SecurityUserRolePermission SECURITY_USER_ROLE_PERMISSION = SecurityUserRolePermission.SECURITY_USER_ROLE_PERMISSION;

    /**
     * The table <code>security.security_user_token</code>.
     */
    public static final SecurityUserToken SECURITY_USER_TOKEN = SecurityUserToken.SECURITY_USER_TOKEN;
}
