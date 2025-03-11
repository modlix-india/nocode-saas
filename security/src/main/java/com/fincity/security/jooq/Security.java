/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq;


import com.fincity.security.jooq.tables.SecurityAddress;
import com.fincity.security.jooq.tables.SecurityApp;
import com.fincity.security.jooq.tables.SecurityAppAccess;
import com.fincity.security.jooq.tables.SecurityAppDependency;
import com.fincity.security.jooq.tables.SecurityAppProperty;
import com.fincity.security.jooq.tables.SecurityAppRegAccess;
import com.fincity.security.jooq.tables.SecurityAppRegDepartment;
import com.fincity.security.jooq.tables.SecurityAppRegDesignation;
import com.fincity.security.jooq.tables.SecurityAppRegFileAccess;
import com.fincity.security.jooq.tables.SecurityAppRegIntegration;
import com.fincity.security.jooq.tables.SecurityAppRegIntegrationTokens;
import com.fincity.security.jooq.tables.SecurityAppRegProfile;
import com.fincity.security.jooq.tables.SecurityAppRegUserDesignation;
import com.fincity.security.jooq.tables.SecurityAppRegUserProfile;
import com.fincity.security.jooq.tables.SecurityClient;
import com.fincity.security.jooq.tables.SecurityClientAddress;
import com.fincity.security.jooq.tables.SecurityClientHierarchy;
import com.fincity.security.jooq.tables.SecurityClientOtpPolicy;
import com.fincity.security.jooq.tables.SecurityClientPasswordPolicy;
import com.fincity.security.jooq.tables.SecurityClientPinPolicy;
import com.fincity.security.jooq.tables.SecurityClientProfile;
import com.fincity.security.jooq.tables.SecurityClientType;
import com.fincity.security.jooq.tables.SecurityClientUrl;
import com.fincity.security.jooq.tables.SecurityDepartment;
import com.fincity.security.jooq.tables.SecurityDesignation;
import com.fincity.security.jooq.tables.SecurityOtp;
import com.fincity.security.jooq.tables.SecurityPastPasswords;
import com.fincity.security.jooq.tables.SecurityPastPins;
import com.fincity.security.jooq.tables.SecurityPermission;
import com.fincity.security.jooq.tables.SecurityProfile;
import com.fincity.security.jooq.tables.SecurityProfileArrangement;
import com.fincity.security.jooq.tables.SecurityProfileUser;
import com.fincity.security.jooq.tables.SecuritySoxLog;
import com.fincity.security.jooq.tables.SecuritySslCertificate;
import com.fincity.security.jooq.tables.SecuritySslChallenge;
import com.fincity.security.jooq.tables.SecuritySslRequest;
import com.fincity.security.jooq.tables.SecurityUser;
import com.fincity.security.jooq.tables.SecurityUserAddress;
import com.fincity.security.jooq.tables.SecurityUserToken;
import com.fincity.security.jooq.tables.SecurityV2Role;
import com.fincity.security.jooq.tables.SecurityV2RolePermission;
import com.fincity.security.jooq.tables.SecurityV2RoleRole;
import com.fincity.security.jooq.tables.SecurityV2UserRole;

import java.util.Arrays;
import java.util.List;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Security extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security</code>
     */
    public static final Security SECURITY = new Security();

    /**
     * The table <code>security.security_address</code>.
     */
    public final SecurityAddress SECURITY_ADDRESS = SecurityAddress.SECURITY_ADDRESS;

    /**
     * The table <code>security.security_app</code>.
     */
    public final SecurityApp SECURITY_APP = SecurityApp.SECURITY_APP;

    /**
     * The table <code>security.security_app_access</code>.
     */
    public final SecurityAppAccess SECURITY_APP_ACCESS = SecurityAppAccess.SECURITY_APP_ACCESS;

    /**
     * The table <code>security.security_app_dependency</code>.
     */
    public final SecurityAppDependency SECURITY_APP_DEPENDENCY = SecurityAppDependency.SECURITY_APP_DEPENDENCY;

    /**
     * The table <code>security.security_app_property</code>.
     */
    public final SecurityAppProperty SECURITY_APP_PROPERTY = SecurityAppProperty.SECURITY_APP_PROPERTY;

    /**
     * The table <code>security.security_app_reg_access</code>.
     */
    public final SecurityAppRegAccess SECURITY_APP_REG_ACCESS = SecurityAppRegAccess.SECURITY_APP_REG_ACCESS;

    /**
     * The table <code>security.security_app_reg_department</code>.
     */
    public final SecurityAppRegDepartment SECURITY_APP_REG_DEPARTMENT = SecurityAppRegDepartment.SECURITY_APP_REG_DEPARTMENT;

    /**
     * The table <code>security.security_app_reg_designation</code>.
     */
    public final SecurityAppRegDesignation SECURITY_APP_REG_DESIGNATION = SecurityAppRegDesignation.SECURITY_APP_REG_DESIGNATION;

    /**
     * The table <code>security.security_app_reg_file_access</code>.
     */
    public final SecurityAppRegFileAccess SECURITY_APP_REG_FILE_ACCESS = SecurityAppRegFileAccess.SECURITY_APP_REG_FILE_ACCESS;

    /**
     * The table <code>security.security_app_reg_integration</code>.
     */
    public final SecurityAppRegIntegration SECURITY_APP_REG_INTEGRATION = SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION;

    /**
     * The table <code>security.security_app_reg_integration_tokens</code>.
     */
    public final SecurityAppRegIntegrationTokens SECURITY_APP_REG_INTEGRATION_TOKENS = SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS;

    /**
     * The table <code>security.security_app_reg_profile</code>.
     */
    public final SecurityAppRegProfile SECURITY_APP_REG_PROFILE = SecurityAppRegProfile.SECURITY_APP_REG_PROFILE;

    /**
     * The table <code>security.security_app_reg_user_designation</code>.
     */
    public final SecurityAppRegUserDesignation SECURITY_APP_REG_USER_DESIGNATION = SecurityAppRegUserDesignation.SECURITY_APP_REG_USER_DESIGNATION;

    /**
     * The table <code>security.security_app_reg_user_profile</code>.
     */
    public final SecurityAppRegUserProfile SECURITY_APP_REG_USER_PROFILE = SecurityAppRegUserProfile.SECURITY_APP_REG_USER_PROFILE;

    /**
     * The table <code>security.security_client</code>.
     */
    public final SecurityClient SECURITY_CLIENT = SecurityClient.SECURITY_CLIENT;

    /**
     * The table <code>security.security_client_address</code>.
     */
    public final SecurityClientAddress SECURITY_CLIENT_ADDRESS = SecurityClientAddress.SECURITY_CLIENT_ADDRESS;

    /**
     * The table <code>security.security_client_hierarchy</code>.
     */
    public final SecurityClientHierarchy SECURITY_CLIENT_HIERARCHY = SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY;

    /**
     * The table <code>security.security_client_otp_policy</code>.
     */
    public final SecurityClientOtpPolicy SECURITY_CLIENT_OTP_POLICY = SecurityClientOtpPolicy.SECURITY_CLIENT_OTP_POLICY;

    /**
     * The table <code>security.security_client_password_policy</code>.
     */
    public final SecurityClientPasswordPolicy SECURITY_CLIENT_PASSWORD_POLICY = SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

    /**
     * The table <code>security.security_client_pin_policy</code>.
     */
    public final SecurityClientPinPolicy SECURITY_CLIENT_PIN_POLICY = SecurityClientPinPolicy.SECURITY_CLIENT_PIN_POLICY;

    /**
     * The table <code>security.security_client_profile</code>.
     */
    public final SecurityClientProfile SECURITY_CLIENT_PROFILE = SecurityClientProfile.SECURITY_CLIENT_PROFILE;

    /**
     * The table <code>security.security_client_type</code>.
     */
    public final SecurityClientType SECURITY_CLIENT_TYPE = SecurityClientType.SECURITY_CLIENT_TYPE;

    /**
     * The table <code>security.security_client_url</code>.
     */
    public final SecurityClientUrl SECURITY_CLIENT_URL = SecurityClientUrl.SECURITY_CLIENT_URL;

    /**
     * The table <code>security.security_department</code>.
     */
    public final SecurityDepartment SECURITY_DEPARTMENT = SecurityDepartment.SECURITY_DEPARTMENT;

    /**
     * The table <code>security.security_designation</code>.
     */
    public final SecurityDesignation SECURITY_DESIGNATION = SecurityDesignation.SECURITY_DESIGNATION;

    /**
     * The table <code>security.security_otp</code>.
     */
    public final SecurityOtp SECURITY_OTP = SecurityOtp.SECURITY_OTP;

    /**
     * The table <code>security.security_past_passwords</code>.
     */
    public final SecurityPastPasswords SECURITY_PAST_PASSWORDS = SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

    /**
     * The table <code>security.security_past_pins</code>.
     */
    public final SecurityPastPins SECURITY_PAST_PINS = SecurityPastPins.SECURITY_PAST_PINS;

    /**
     * The table <code>security.security_permission</code>.
     */
    public final SecurityPermission SECURITY_PERMISSION = SecurityPermission.SECURITY_PERMISSION;

    /**
     * The table <code>security.security_profile</code>.
     */
    public final SecurityProfile SECURITY_PROFILE = SecurityProfile.SECURITY_PROFILE;

    /**
     * The table <code>security.security_profile_arrangement</code>.
     */
    public final SecurityProfileArrangement SECURITY_PROFILE_ARRANGEMENT = SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT;

    /**
     * The table <code>security.security_profile_user</code>.
     */
    public final SecurityProfileUser SECURITY_PROFILE_USER = SecurityProfileUser.SECURITY_PROFILE_USER;

    /**
     * The table <code>security.security_sox_log</code>.
     */
    public final SecuritySoxLog SECURITY_SOX_LOG = SecuritySoxLog.SECURITY_SOX_LOG;

    /**
     * The table <code>security.security_ssl_certificate</code>.
     */
    public final SecuritySslCertificate SECURITY_SSL_CERTIFICATE = SecuritySslCertificate.SECURITY_SSL_CERTIFICATE;

    /**
     * The table <code>security.security_ssl_challenge</code>.
     */
    public final SecuritySslChallenge SECURITY_SSL_CHALLENGE = SecuritySslChallenge.SECURITY_SSL_CHALLENGE;

    /**
     * The table <code>security.security_ssl_request</code>.
     */
    public final SecuritySslRequest SECURITY_SSL_REQUEST = SecuritySslRequest.SECURITY_SSL_REQUEST;

    /**
     * The table <code>security.security_user</code>.
     */
    public final SecurityUser SECURITY_USER = SecurityUser.SECURITY_USER;

    /**
     * The table <code>security.security_user_address</code>.
     */
    public final SecurityUserAddress SECURITY_USER_ADDRESS = SecurityUserAddress.SECURITY_USER_ADDRESS;

    /**
     * The table <code>security.security_user_token</code>.
     */
    public final SecurityUserToken SECURITY_USER_TOKEN = SecurityUserToken.SECURITY_USER_TOKEN;

    /**
     * The table <code>security.security_v2_role</code>.
     */
    public final SecurityV2Role SECURITY_V2_ROLE = SecurityV2Role.SECURITY_V2_ROLE;

    /**
     * The table <code>security.security_v2_role_permission</code>.
     */
    public final SecurityV2RolePermission SECURITY_V2_ROLE_PERMISSION = SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION;

    /**
     * The table <code>security.security_v2_role_role</code>.
     */
    public final SecurityV2RoleRole SECURITY_V2_ROLE_ROLE = SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE;

    /**
     * The table <code>security.security_v2_user_role</code>.
     */
    public final SecurityV2UserRole SECURITY_V2_USER_ROLE = SecurityV2UserRole.SECURITY_V2_USER_ROLE;

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
            SecurityAddress.SECURITY_ADDRESS,
            SecurityApp.SECURITY_APP,
            SecurityAppAccess.SECURITY_APP_ACCESS,
            SecurityAppDependency.SECURITY_APP_DEPENDENCY,
            SecurityAppProperty.SECURITY_APP_PROPERTY,
            SecurityAppRegAccess.SECURITY_APP_REG_ACCESS,
            SecurityAppRegDepartment.SECURITY_APP_REG_DEPARTMENT,
            SecurityAppRegDesignation.SECURITY_APP_REG_DESIGNATION,
            SecurityAppRegFileAccess.SECURITY_APP_REG_FILE_ACCESS,
            SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION,
            SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS,
            SecurityAppRegProfile.SECURITY_APP_REG_PROFILE,
            SecurityAppRegUserDesignation.SECURITY_APP_REG_USER_DESIGNATION,
            SecurityAppRegUserProfile.SECURITY_APP_REG_USER_PROFILE,
            SecurityClient.SECURITY_CLIENT,
            SecurityClientAddress.SECURITY_CLIENT_ADDRESS,
            SecurityClientHierarchy.SECURITY_CLIENT_HIERARCHY,
            SecurityClientOtpPolicy.SECURITY_CLIENT_OTP_POLICY,
            SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY,
            SecurityClientPinPolicy.SECURITY_CLIENT_PIN_POLICY,
            SecurityClientProfile.SECURITY_CLIENT_PROFILE,
            SecurityClientType.SECURITY_CLIENT_TYPE,
            SecurityClientUrl.SECURITY_CLIENT_URL,
            SecurityDepartment.SECURITY_DEPARTMENT,
            SecurityDesignation.SECURITY_DESIGNATION,
            SecurityOtp.SECURITY_OTP,
            SecurityPastPasswords.SECURITY_PAST_PASSWORDS,
            SecurityPastPins.SECURITY_PAST_PINS,
            SecurityPermission.SECURITY_PERMISSION,
            SecurityProfile.SECURITY_PROFILE,
            SecurityProfileArrangement.SECURITY_PROFILE_ARRANGEMENT,
            SecurityProfileUser.SECURITY_PROFILE_USER,
            SecuritySoxLog.SECURITY_SOX_LOG,
            SecuritySslCertificate.SECURITY_SSL_CERTIFICATE,
            SecuritySslChallenge.SECURITY_SSL_CHALLENGE,
            SecuritySslRequest.SECURITY_SSL_REQUEST,
            SecurityUser.SECURITY_USER,
            SecurityUserAddress.SECURITY_USER_ADDRESS,
            SecurityUserToken.SECURITY_USER_TOKEN,
            SecurityV2Role.SECURITY_V2_ROLE,
            SecurityV2RolePermission.SECURITY_V2_ROLE_PERMISSION,
            SecurityV2RoleRole.SECURITY_V2_ROLE_ROLE,
            SecurityV2UserRole.SECURITY_V2_USER_ROLE
        );
    }
}
