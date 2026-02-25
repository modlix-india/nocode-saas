package com.fincity.security.testutil;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.Designation;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.dto.Otp;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.enums.SecurityAppStatus;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.RequestUpdatePassword;

public class TestDataFactory {

	private TestDataFactory() {
	}

	// --- Client ---

	public static Client createClient(ULong id, String code, String typeCode, SecurityClientStatusCode status) {
		Client client = new Client();
		client.setId(id);
		client.setCode(code);
		client.setName("Client " + code);
		client.setTypeCode(typeCode);
		client.setStatusCode(status);
		client.setTokenValidityMinutes(60);
		client.setLocaleCode("en");
		return client;
	}

	public static Client createSystemClient() {
		return createClient(ULong.valueOf(1), "SYSTEM", "SYS", SecurityClientStatusCode.ACTIVE);
	}

	public static Client createBusinessClient(ULong id, String code) {
		return createClient(id, code, "BUS", SecurityClientStatusCode.ACTIVE);
	}

	public static Client createIndividualClient(ULong id, String code) {
		return createClient(id, code, "INDV", SecurityClientStatusCode.ACTIVE);
	}

	// --- User ---

	public static User createUser(ULong id, ULong clientId, String userName, String email,
			SecurityUserStatusCode status) {
		User user = new User();
		user.setId(id);
		user.setClientId(clientId);
		user.setUserName(userName);
		user.setEmailId(email);
		user.setFirstName("Test");
		user.setLastName("User");
		user.setStatusCode(status);
		user.setAccountNonExpired(true);
		user.setAccountNonLocked(true);
		user.setCredentialsNonExpired(true);
		user.setNoFailedAttempt((short) 0);
		user.setNoPinFailedAttempt((short) 0);
		user.setNoOtpFailedAttempt((short) 0);
		user.setNoOtpResendAttempts((short) 0);
		return user;
	}

	public static User createActiveUser(ULong id, ULong clientId) {
		return createUser(id, clientId, "testuser" + id, "test" + id + "@test.com", SecurityUserStatusCode.ACTIVE);
	}

	public static User createLockedUser(ULong id, ULong clientId, LocalDateTime lockUntil) {
		User user = createUser(id, clientId, "lockeduser", "locked@test.com", SecurityUserStatusCode.LOCKED);
		user.setLockedUntil(lockUntil);
		user.setLockedDueTo("Too many failed attempts");
		return user;
	}

	public static User createPasswordExpiredUser(ULong id, ULong clientId) {
		return createUser(id, clientId, "expireduser", "expired@test.com", SecurityUserStatusCode.PASSWORD_EXPIRED);
	}

	public static User createInactiveUser(ULong id, ULong clientId) {
		return createUser(id, clientId, "inactiveuser", "inactive@test.com", SecurityUserStatusCode.INACTIVE);
	}

	public static User createDeletedUser(ULong id, ULong clientId) {
		return createUser(id, clientId, "deleteduser", "deleted@test.com", SecurityUserStatusCode.DELETED);
	}

	// --- App ---

	public static App createApp(ULong id, ULong clientId, String appCode, String appName,
			SecurityAppAppAccessType accessType) {
		App app = new App();
		app.setId(id);
		app.setClientId(clientId);
		app.setAppCode(appCode);
		app.setAppName(appName);
		app.setAppType(SecurityAppAppType.APP);
		app.setAppAccessType(accessType);
		app.setStatus(SecurityAppStatus.ACTIVE);
		return app;
	}

	public static App createOwnApp(ULong id, ULong clientId, String appCode) {
		return createApp(id, clientId, appCode, "App " + appCode, SecurityAppAppAccessType.OWN);
	}

	public static App createExplicitApp(ULong id, ULong clientId, String appCode) {
		return createApp(id, clientId, appCode, "Explicit App " + appCode, SecurityAppAppAccessType.EXPLICIT);
	}

	// --- RoleV2 ---

	public static RoleV2 createRoleV2(ULong id, ULong clientId, ULong appId, String name) {
		RoleV2 role = new RoleV2();
		role.setId(id);
		role.setClientId(clientId);
		role.setAppId(appId);
		role.setName(name);
		role.setShortName(name.toUpperCase());
		role.setDescription("Role: " + name);
		return role;
	}

	// --- Profile ---

	public static Profile createProfile(ULong id, ULong clientId, ULong appId, String name) {
		Profile profile = new Profile();
		profile.setId(id);
		profile.setClientId(clientId);
		profile.setAppId(appId);
		profile.setName(name);
		profile.setTitle("Profile " + name);
		return profile;
	}

	// --- TokenObject ---

	public static TokenObject createTokenObject(ULong id, ULong userId, String token, LocalDateTime expiresAt) {
		TokenObject tokenObject = new TokenObject();
		tokenObject.setId(id);
		tokenObject.setUserId(userId);
		tokenObject.setToken(token);
		tokenObject.setPartToken(token.length() > 50 ? token.substring(token.length() - 50) : token);
		tokenObject.setExpiresAt(expiresAt);
		tokenObject.setIpAddress("127.0.0.1");
		return tokenObject;
	}

	// --- Policies ---

	public static ClientPasswordPolicy createPasswordPolicy() {
		ClientPasswordPolicy policy = new ClientPasswordPolicy();
		policy.setAtleastOneUppercase(true);
		policy.setAtleastOneLowercase(true);
		policy.setAtleastOneDigit(true);
		policy.setAtleastOneSpecialChar(true);
		policy.setSpacesAllowed(false);
		policy.setPassMinLength((short) 12);
		policy.setPassMaxLength((short) 20);
		policy.setPassHistoryCount((short) 5);
		policy.setPassExpiryInDays((short) 10);
		policy.setPassExpiryWarnInDays((short) 8);
		policy.setNoFailedAttempts((short) 3);
		policy.setUserLockTime(15L);
		return policy;
	}

	public static ClientPinPolicy createPinPolicy() {
		ClientPinPolicy policy = new ClientPinPolicy();
		policy.setLength((short) 6);
		policy.setPinHistoryCount((short) 3);
		policy.setNoFailedAttempts((short) 3);
		policy.setUserLockTime(15L);
		return policy;
	}

	public static ClientOtpPolicy createOtpPolicy() {
		ClientOtpPolicy policy = new ClientOtpPolicy();
		policy.setLength((short) 4);
		policy.setNumeric(true);
		policy.setExpireInterval(5L);
		policy.setNoResendAttempts((short) 3);
		policy.setNoFailedAttempts((short) 3);
		policy.setUserLockTime(15L);
		return policy;
	}

	// --- ClientHierarchy ---

	public static ClientHierarchy createClientHierarchy(ULong clientId, ULong level0, ULong level1, ULong level2,
			ULong level3) {
		ClientHierarchy ch = new ClientHierarchy();
		ch.setClientId(clientId);
		ch.setManageClientLevel0(level0);
		ch.setManageClientLevel1(level1);
		ch.setManageClientLevel2(level2);
		ch.setManageClientLevel3(level3);
		return ch;
	}

	public static ClientHierarchy createSystemHierarchy(ULong clientId) {
		return createClientHierarchy(clientId, null, null, null, null);
	}

	public static ClientHierarchy createLevel0Hierarchy(ULong clientId, ULong parent) {
		return createClientHierarchy(clientId, parent, null, null, null);
	}

	// --- ClientManager ---

	public static ClientManager createClientManager(ULong id, ULong clientId, ULong managerId) {
		ClientManager cm = new ClientManager();
		cm.setId(id);
		cm.setClientId(clientId);
		cm.setManagerId(managerId);
		return cm;
	}

	// --- ContextAuthentication ---

	public static ContextAuthentication createContextAuthentication(ULong userId, ULong clientId, String clientCode,
			String typeCode, boolean isAuthenticated, List<String> authorities) {
		ContextUser user = new ContextUser();
		user.setId(BigInteger.valueOf(userId.longValue()));
		user.setClientId(BigInteger.valueOf(clientId.longValue()));
		user.setUserName("testuser");
		user.setEmailId("test@test.com");
		user.setFirstName("Test");
		user.setLastName("User");
		user.setStatusCode(SecurityUserStatusCode.ACTIVE.getLiteral());
		user.setStringAuthorities(authorities);

		ContextAuthentication ca = new ContextAuthentication();
		ca.setUser(user);
		ca.setAuthenticated(isAuthenticated);
		ca.setClientCode(clientCode);
		ca.setClientTypeCode(typeCode);
		ca.setLoggedInFromClientId(BigInteger.valueOf(clientId.longValue()));
		ca.setLoggedInFromClientCode(clientCode);
		return ca;
	}

	public static ContextAuthentication createSystemAuth() {
		return createContextAuthentication(ULong.valueOf(1), ULong.valueOf(1), "SYSTEM", "SYS", true,
				List.of("Authorities.ROLE_Owner", "Authorities.Client_CREATE", "Authorities.Client_READ",
						"Authorities.Client_UPDATE", "Authorities.Client_DELETE", "Authorities.User_CREATE",
						"Authorities.User_READ", "Authorities.User_UPDATE", "Authorities.User_DELETE",
						"Authorities.Application_CREATE", "Authorities.Application_READ",
						"Authorities.Application_UPDATE", "Authorities.Application_DELETE",
						"Authorities.Role_CREATE", "Authorities.Role_READ", "Authorities.Role_UPDATE",
						"Authorities.Role_DELETE", "Authorities.Profile_CREATE", "Authorities.Profile_READ",
						"Authorities.Profile_UPDATE", "Authorities.Profile_DELETE", "Authorities.Logged_IN"));
	}

	public static ContextAuthentication createBusinessAuth(ULong clientId, String clientCode,
			List<String> authorities) {
		return createContextAuthentication(ULong.valueOf(10), clientId, clientCode, "BUS", true, authorities);
	}

	// --- AuthenticationRequest ---

	public static AuthenticationRequest createAuthenticationRequest(String userName, String password) {
		AuthenticationRequest request = new AuthenticationRequest();
		request.setUserName(userName);
		request.setPassword(password);
		return request;
	}

	public static AuthenticationRequest createAuthenticationRequestWithOtp(String userName, String otp) {
		AuthenticationRequest request = new AuthenticationRequest();
		request.setUserName(userName);
		request.setOtp(otp);
		request.setGenerateOtp(false);
		return request;
	}

	public static AuthenticationRequest createAuthenticationRequestWithPin(String userName, String pin) {
		AuthenticationRequest request = new AuthenticationRequest();
		request.setUserName(userName);
		request.setPin(pin);
		return request;
	}

	// --- RequestUpdatePassword ---

	public static RequestUpdatePassword createRequestUpdatePassword(String oldPassword, String newPassword) {
		RequestUpdatePassword request = new RequestUpdatePassword();
		request.setOldPassword(oldPassword);
		request.setNewPassword(newPassword);
		return request;
	}

	// --- OneTimeToken ---

	public static OneTimeToken createOneTimeToken(ULong id, ULong userId, String token) {
		OneTimeToken ott = new OneTimeToken();
		ott.setId(id);
		ott.setUserId(userId);
		ott.setToken(token);
		return ott;
	}

	// --- Department ---

	public static Department createDepartment(ULong id, ULong clientId, String name) {
		Department dept = new Department();
		dept.setId(id);
		dept.setClientId(clientId);
		dept.setName(name);
		return dept;
	}

	// --- Designation ---

	public static Designation createDesignation(ULong id, ULong clientId, String name) {
		Designation desg = new Designation();
		desg.setId(id);
		desg.setClientId(clientId);
		desg.setName(name);
		return desg;
	}

	// --- Otp ---

	public static Otp createOtp(ULong id, ULong userId) {
		Otp otpEntity = new Otp();
		otpEntity.setId(id);
		otpEntity.setUserId(userId);
		otpEntity.setEmailId("test@test.com");
		return otpEntity;
	}
}
