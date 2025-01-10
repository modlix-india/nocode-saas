package com.fincity.security.service.appregistration;

import static com.fincity.saas.commons.util.StringUtil.safeIsBlank;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.feign.IFeignFilesService;
import com.fincity.security.jooq.enums.SecurityAppAppUsageType;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.ClientRegistrationResponse;
import com.fincity.security.model.OtpGenerationRequest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
import com.fincity.security.service.OtpService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.UserService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class ClientRegistrationService {

	private final ClientDAO dao;
	private final AppService appService;
	private final UserService userService;
	private final OtpService otpService;
	private final AuthenticationService authenticationService;
	private final ClientService clientService;
	private final ClientHierarchyService clientHierarchyService;
	private final EventCreationService ecService;
	private final ClientUrlService clientUrlService;
	private final AppRegistrationDAO appRegistrationDAO;
	private final IFeignFilesService filesService;
	private final AppRegistrationIntegrationService appRegistrationIntegrationService;
	private final AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;

	private final SecurityMessageResourceService securityMessageResourceService;

	private static final int VALIDITY_MINUTES = 30;

	private static final String SOCIAL_CALLBACK_URI = "/api/security/clients/socialRegister/callback";

	@Value("${security.subdomain.endings}")
	private String subDomainEndings;

	@Value("${security.register.legs.counts}")
	private Short registerLegsCount;

	private static final String FEATURE_NOT_SUPPORTED = "Feature not supported";

	public ClientRegistrationService(ClientDAO dao, AppService appService, UserService userService,
			OtpService otpService, AuthenticationService authenticationService, ClientService clientService,
			ClientHierarchyService clientHierarchyService, EventCreationService ecService,
			ClientUrlService clientUrlService, AppRegistrationDAO appRegistrationDAO, IFeignFilesService filesService,
			AppRegistrationIntegrationService appRegistrationIntegrationService,
			AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService,
			SecurityMessageResourceService securityMessageResourceService) {

		this.dao = dao;
		this.appService = appService;
		this.userService = userService;
		this.otpService = otpService;
		this.authenticationService = authenticationService;
		this.clientService = clientService;
		this.clientHierarchyService = clientHierarchyService;
		this.ecService = ecService;
		this.clientUrlService = clientUrlService;
		this.appRegistrationDAO = appRegistrationDAO;
		this.filesService = filesService;
		this.appRegistrationIntegrationService = appRegistrationIntegrationService;
		this.appRegistrationIntegrationTokenService = appRegistrationIntegrationTokenService;
		this.securityMessageResourceService = securityMessageResourceService;
	}

	private <T> Mono<T> regError(Object... params) {
		return this.securityMessageResourceService.throwMessage(
				msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, params);
	}

	public Mono<Boolean> generateOtp(OtpGenerationRequest otpGenerationRequest, ServerHttpRequest request) {

		String appCode = request.getHeaders().getFirst("appCode");
		String clientCode = request.getHeaders().getFirst("clientCode");

		return FlatMapUtil.flatMapMono(
				() -> clientService.getClientBy(clientCode),

				client -> this.fetchAppProp(client.getId(), null,
						appCode, AppService.APP_PROP_REG_TYPE),

				(client, regProp) -> {

					if (!regProp.equals(AppService.APP_PROP_REG_TYPE_VERIFICATION))
						return this.regError(FEATURE_NOT_SUPPORTED);

					return otpService.generateOtp(
							otpGenerationRequest.setPurpose(OtpPurpose.REGISTRATION)
									.setVerifyLegsCounts(registerLegsCount),
							request);
				})
				.switchIfEmpty(regError("Feature not supported"))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.generateOtp"));
	}

	public Mono<Boolean> preRegisterCheckOne(ClientRegistrationRequest registrationRequest) {

		if (registerLegsCount == null || registerLegsCount > 1)
			return this.regError(FEATURE_NOT_SUPPORTED);

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.preRegisterCheck(registrationRequest, ca),

				(ca, subDomain) -> this.fetchAppProp(ULong.valueOf(ca.getLoggedInFromClientId()), null,
						ca.getUrlAppCode(), AppService.APP_PROP_REG_TYPE),
				(ca, subDomain, regProp) -> {

					if (safeIsBlank(regProp) || AppService.APP_PROP_REG_TYPE_NO_REGISTRATION.equals(regProp))
						return this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								SecurityMessageResourceService.NO_REGISTRATION_AVAILABLE);

					return this.verifyClient(ca, regProp, registrationRequest.getEmailId(),
							registrationRequest.getPhoneNumber(), registrationRequest.getUniqueCode());
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.register"));
	}

	public Mono<ClientRegistrationResponse> register(ClientRegistrationRequest registrationRequest,
			ServerHttpRequest request, ServerHttpResponse response) {

		String urlPrefix = getUrlPrefix(request);

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.preRegisterCheck(registrationRequest, ca),

				(ca, subDomain) -> this.fetchAppProp(ULong.valueOf(ca.getLoggedInFromClientId()), null,
						ca.getUrlAppCode(), AppService.APP_PROP_REG_TYPE),

				(ca, subDomain, regProp) -> this.registerClient(registrationRequest, ca, regProp),

				(ca, subDomain, regProp, client) -> this.clientService.getClientAppPolicy(
						ULong.valueOf(ca.getLoggedInFromClientId()), ca.getUrlAppCode(),
						registrationRequest.getPasswordType()),

				(ca, subDomain, regProp, client, policy) -> this.registerUser(
						ca.getUrlAppCode(), ULong.valueOf(ca.getLoggedInFromClientId()), registrationRequest, client,
						policy),

				(ca, subDomain, regProp, client, policy, userTuple) -> {
					if (safeIsBlank(registrationRequest.getSocialRegisterState())
							&& registrationRequest.getPasswordType() != null)
						return this.userService.makeOneTimeToken(request, ca, userTuple.getT1(),
								ULong.valueOf(ca.getLoggedInFromClientId())).map(TokenObject::getToken);

					return Mono.just("");
				},
				(ca, subDomain, regProp, client, policy, userTuple, token) -> this.addFilesAccessPath(ca, client),

				(ca, subDomain, regProp, client, policy, userTuple, token, filesAccessCreated) -> this
						.createRegistrationEvents(ca, client, subDomain, urlPrefix, userTuple.getT1(), token,
								userTuple.getT2())
						.flatMap(events -> this.getClientRegistrationResponse(registrationRequest,
								userTuple.getT1().getId(), userTuple.getT2(), request, response)),

				(ca, subDomain, regProp, client, policy, userTuple, token, filesAccessCreated,
						res) -> {

					if (safeIsBlank(subDomain))
						return Mono.just(res);

					return this.clientUrlService.createForRegistration(
							new ClientUrl().setAppCode(ca.getUrlAppCode())
									.setUrlPattern(subDomain).setClientId(client.getId()))
							.map(e -> res.setRedirectURL(subDomain));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.register"));
	}

	private String getUrlPrefix(ServerHttpRequest request) {

		String host = request.getHeaders().getFirst("X-Forwarded-Host");
		String scheme = request.getHeaders().getFirst("X-Forwarded-Proto");
		String port = request.getHeaders().getFirst("X-Forwarded-Port");

		return (scheme != null && scheme.contains("https")) ? "https://" + host : "http://" + host + ":" + port;
	}

	private Mono<String> preRegisterCheck(ClientRegistrationRequest registrationRequest, ContextAuthentication ca) {

		if (registrationRequest.isBusinessClient() && safeIsBlank(registrationRequest.getBusinessType()))
			registrationRequest.setBusinessType(AppRegistrationService.DEFAULT_BUSINESS_TYPE);

		if (ca.isAuthenticated())
			return this.regError("Signout to register");

		Mono<App> appMono = this.appService.getAppByCode(ca.getUrlAppCode());

		Mono<Client> clientMono = this.clientService.getClientBy(ca.getLoggedInFromClientCode());

		Mono<ClientLevelType> clientLevelTypeMono = appMono.flatMap(app -> this.clientService
				.getClientLevelType(ULong.valueOf(ca.getLoggedInFromClientId()), app.getId()));

		Mono<Boolean> checkIfUserExists = registrationRequest.isBusinessClient() ? Mono.just(Boolean.TRUE)
				: this.userService
						.checkIndividualClientUser(ca.getUrlClientCode(), registrationRequest)
						.filter(e -> !e).switchIfEmpty(this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.CONFLICT, msg),
								SecurityMessageResourceService.USER_ALREADY_EXISTS, registrationRequest.getEmailId()));

		return FlatMapUtil.flatMapMono(

				() -> checkIfUserExists,

				exists -> appMono,

				(exists, app) -> clientMono,

				(exists, app, client) -> clientLevelTypeMono,

				(exists, app, client, levelType) -> this.checkUsageType(app.getAppUsageType(),
						levelType, registrationRequest.isBusinessClient()),

				(exists, app, client, levelType, usageType) -> this.fetchAppProp(
						ULong.valueOf(ca.getLoggedInFromClientId()), app.getId(), null, AppService.APP_PROP_URL_SUFFIX),

				(exists, app, client, levelType, usageType, suffix) -> this
						.checkSubDomainAvailability(this.subDomainEndings, registrationRequest.getSubDomain(),
								registrationRequest.isBusinessClient()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.preRegisterCheck"));
	}

	private Mono<Boolean> checkUsageType(SecurityAppAppUsageType usageType, ClientLevelType levelType,
			boolean isBusinessClient) {

		switch (usageType) {

			case S:
				return this.regError("Not allowed for Standalone Applications");
			case B:
				return this.regError("Not allowed for Business Applications");
			case B2C:
				if (levelType != ClientLevelType.OWNER)
					return this.regError("Only Applications owner can register for B2C Applications");

				if (isBusinessClient)
					return this.regError("Business clients are not allowed for B2C Applications");

				break;
			case B2B:
				if (levelType != ClientLevelType.OWNER)
					return this.regError("Only Applications owner can register for B2B Applications");

				if (!isBusinessClient)
					return this.regError("Individual clients are not allowed for B2B Applications");

				break;
			case B2X:
				if (levelType != ClientLevelType.OWNER)
					return this.regError("Only Applications owner can register for B2X Applications");

				break;
			case B2B2B:
				if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
					return this.regError("Only Applications owner can register for B2B2B Applications");

				if (!isBusinessClient)
					return this.regError("Individual clients are not allowed for B2B2B Applications");

				break;
			case B2B2C:
				if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
					return this.regError("Only Applications owner can register for B2B2C Applications");

				if (levelType == ClientLevelType.OWNER && !isBusinessClient)
					return this.regError("Business clients are required for B2B2C Applications at owner level");

				if (levelType == ClientLevelType.CLIENT && isBusinessClient)
					return this.regError("Business clients are not allowed for B2B2C Applications");

				break;
			case B2B2X:
				if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
					return this.regError("Only Applications owner can register for B2B2X Applications");

				if (levelType == ClientLevelType.OWNER && !isBusinessClient)
					return this.regError("Business clients are required for B2B2X Applications at owner level");

				break;
			case B2X2C:
				if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
					return this.regError("Only Applications owner can register for B2X2C Applications");

				if (levelType == ClientLevelType.CLIENT && isBusinessClient)
					return this.regError("Business clients are not allowed for B2X2C Applications");

				break;
			case B2X2X:
				if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
					return this.regError("Only Applications owner can register for B2X2X Applications");

				break;
			default:
				return this.regError("Invalid Application Usage Type");
		}

		return Mono.just(Boolean.TRUE);
	}

	private Mono<String> fetchAppProp(ULong clientId, ULong appId, String appCode, String propName) {

		return this.appService.getProperties(clientId, appId, appCode, propName)
				.map(props -> {

					if (props.isEmpty())
						return "";

					if (props.containsKey(clientId))
						return props.get(clientId).get(AppService.APP_PROP_REG_TYPE).getValue();

					return props.values().stream().findFirst()
							.map(prop -> prop.get(AppService.APP_PROP_REG_TYPE).getValue())
							.orElse("");
				});
	}

	private Mono<String> checkSubDomainAvailability(String suffix, String subDomain, boolean isBusinessClient) {

		if (!isBusinessClient || safeIsBlank(subDomain))
			return Mono.just("");

		String subDomainWithSuffix = safeIsBlank(suffix) ? subDomain + this.subDomainEndings
				: subDomain + suffix;

		return this.clientUrlService.checkSubDomainAvailability(subDomainWithSuffix).filter(e -> e)
				.map(e -> subDomainWithSuffix)
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.CONFLICT, msg),
						SecurityMessageResourceService.SUBDOMAIN_ALREADY_EXISTS, subDomain));
	}

	private Mono<Client> registerClient(ClientRegistrationRequest request, ContextAuthentication ca, String regProp) {

		if (safeIsBlank(regProp) || AppService.APP_PROP_REG_TYPE_NO_REGISTRATION.equals(regProp))
			return this.securityMessageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.NO_REGISTRATION_AVAILABLE);

		Client client = new Client();

		String clientName = getValidClientName(request);

		if (safeIsBlank(clientName))
			return this.securityMessageResourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.FIELDS_MISSING);

		client.setName(clientName);
		client.setTypeCode(request.isBusinessClient() ? "BUS" : "INDV");
		client.setLocaleCode(request.getLocaleCode());
		client.setTokenValidityMinutes(VALIDITY_MINUTES);

		if (safeIsBlank(client.getName()))
			return this.regError("Client name cannot be blank");

		ULong loggedInFromClientId = ULongUtil.valueOf(ca.getLoggedInFromClientId());

		return FlatMapUtil.flatMapMono(

				() -> this.verifyClient(ca, regProp, request.getEmailId(), request.getPhoneNumber(),
						request.getUniqueCode()),

				isVerified -> this.appService.getAppByCode(ca.getUrlAppCode()),

				(isVerified, app) -> this.dao.getValidClientCode(client.getName()).map(client::setCode),

				(isVerified, app, c) -> this.clientService.createForRegistration(c),

				(isVerified, app, c, createdClient) -> this.clientHierarchyService
						.create(loggedInFromClientId, createdClient.getId()),

				(isVerified, app, c, createdClient, clientHierarchy) -> this.clientService
						.addClientPackagesAfterRegistration(app.getId(), app.getClientId(), loggedInFromClientId,
								createdClient),

				(isVerified, app, c, createdClient, clientHierarchy, packagesAdded) -> this.appService
						.addClientAccessAfterRegistration(ca.getUrlAppCode(), loggedInFromClientId, createdClient)
						.map(clientAccessAdded -> createdClient))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.registerClient"));
	}

	/**
	 * We will not verify client if regProp is
	 * {@code AppService.APP_PROP_REG_TYPE_NO_VERIFICATION}
	 * or
	 * We have an authenticated client. In this client will be registered under this
	 * client.
	 */
	private Mono<Boolean> verifyClient(ContextAuthentication ca, String regProp, String emailId, String phoneNumber,
			String uniqueCode) {

		if (regProp.equals(AppService.APP_PROP_REG_TYPE_NO_VERIFICATION))
			return Mono.just(Boolean.TRUE);

		if (ca.isAuthenticated())
			return Mono.just(Boolean.TRUE);

		return this.otpService
				.verifyOtpInternal(ca.getUrlAppCode(), emailId, phoneNumber, OtpPurpose.REGISTRATION, uniqueCode)
				.flatMap(isVerified -> Mono.justOrEmpty(Boolean.TRUE.equals(isVerified) ? Boolean.TRUE : null))
				.switchIfEmpty(this.securityMessageResourceService.throwMessage(
						msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						SecurityMessageResourceService.USER_PASSWORD_INVALID, AuthenticationPasswordType.OTP.getName(),
						AuthenticationPasswordType.OTP.getName()))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.verifyClient"));
	}

	private String getValidClientName(ClientRegistrationRequest request) {

		if (!safeIsBlank(request.getClientName()))
			return request.getClientName();

		if (!safeIsBlank(request.getFirstName()) || !safeIsBlank(request.getLastName()))
			return (StringUtil.safeValueOf(request.getFirstName(), "")
					+ StringUtil.safeValueOf(request.getLastName(), ""));

		if (!safeIsBlank(request.getEmailId()))
			return request.getEmailId();

		if (!safeIsBlank(request.getUserName()))
			return request.getUserName();

		return null;
	}

	private Mono<Tuple2<User, String>> registerUser(String appCode, ULong urlClientId,
			ClientRegistrationRequest request, Client client, AbstractPolicy clientPolicy) {

		User user = new User();
		user.setClientId(client.getId());
		user.setEmailId(request.getEmailId());
		user.setFirstName(request.getFirstName());
		user.setLastName(request.getLastName());
		user.setLocaleCode(request.getLocaleCode());
		user.setUserName(request.getUserName());
		user.setPhoneNumber(request.getPhoneNumber());

		String pass = generatePassword(request, clientPolicy, user);

		if (pass == null)
			return regError("Client password cannot be blank");

		user.setStatusCode(SecurityUserStatusCode.ACTIVE);

		return this.appService.getAppByCode(appCode)
				.flatMap(app -> this.userService
						.createForRegistration(app.getId(), app.getClientId(), urlClientId, client, user,
								request.getPasswordType())
						.map(usr -> Tuples.of(usr, pass)));
	}

	private String generatePassword(ClientRegistrationRequest request, AbstractPolicy clientPasswordPolicy, User user) {

		return switch (request.getPasswordType()) {
			case PASSWORD:
				String password = safeIsBlank(request.getPassword()) ? clientPasswordPolicy.generate()
						: request.getPassword();
				user.setPassword(password);
				yield password;
			case PIN:
				String pin = safeIsBlank(request.getPin()) ? clientPasswordPolicy.generate()
						: request.getPin();
				user.setPin(pin);
				yield pin;
			default:
				yield null;
		};
	}

	private Mono<Boolean> addFilesAccessPath(ContextAuthentication ca, Client client) {

		return FlatMapUtil.flatMapMono(

				() -> this.appService.getAppByCode(ca.getUrlAppCode()),

				app -> this.clientService.getClientLevelType(client.getId(), app.getId()),

				(app, levelType) -> this.appRegistrationDAO.getFileAccessForRegistration(app.getId(), app.getClientId(),
						ULong.valueOf(ca.getLoggedInFromClientId()), client.getTypeCode(), levelType,
						client.getBusinessType()),

				(app, levelType, filesAccess) -> Flux.fromIterable(filesAccess).map(e -> {
					IFeignFilesService.FilesAccessPath accessPath = new IFeignFilesService.FilesAccessPath();
					accessPath.setClientCode(client.getCode());
					accessPath.setAccessName(e.getAccessName());
					accessPath.setWriteAccess(e.isWriteAccess());
					accessPath.setPath(e.getPath());
					accessPath.setAllowSubPathAccess(e.isAllowSubPathAccess());
					accessPath.setResourceType(e.getResourceType());
					return accessPath;
				}).flatMap(filesService::createInternalAccessPath).collectList().map(e -> true))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.addFilesAccessPath"));
	}

	private Mono<Boolean> createRegistrationEvents(ContextAuthentication ca, Client client, String subDomain,
			String urlPrefix, User user, String token, String passwordUsed) {

		Map<String, Object> clientEventData = Map.of(
				"client", client, "subDomain", subDomain, "urlPrefix", urlPrefix);

		Map<String, Object> userEventData = Map.of(
				"client", client, "subDomain", subDomain, "urlPrefix", urlPrefix,
				"user", user, "token", token, "passwordUsed", passwordUsed);

		EventQueObject clientRegisteredEvent = new EventQueObject()
				.setAppCode(ca.getUrlAppCode())
				.setClientCode(ca.getLoggedInFromClientCode())
				.setEventName(EventNames.CLIENT_REGISTERED)
				.setData(clientEventData);

		EventQueObject userRegisteredEvent = new EventQueObject()
				.setAppCode(ca.getUrlAppCode())
				.setClientCode(ca.getLoggedInFromClientCode())
				.setEventName(EventNames.USER_REGISTERED)
				.setData(userEventData);

		return Mono.zip(
				this.ecService.createEvent(clientRegisteredEvent).flatMap(BooleanUtil::safeValueOfWithEmpty),
				this.ecService.createEvent(userRegisteredEvent).flatMap(BooleanUtil::safeValueOfWithEmpty))
				.thenReturn(Boolean.TRUE)
				.onErrorReturn(Boolean.FALSE);
	}

	private Mono<ClientRegistrationResponse> getClientRegistrationResponse(
			ClientRegistrationRequest registrationRequest, ULong userId, String password, ServerHttpRequest request,
			ServerHttpResponse response) {

		return this.getClientAuthenticationResponse(registrationRequest, userId, password, request, response)
				.flatMap(auth -> Mono.just(new ClientRegistrationResponse(true, userId, "", auth)))
				.switchIfEmpty(Mono.just(new ClientRegistrationResponse(true, userId, "", null)));
	}

	private Mono<AuthenticationResponse> getClientAuthenticationResponse(ClientRegistrationRequest registrationRequest,
			ULong userId, String password, ServerHttpRequest request, ServerHttpResponse response) {

		AuthenticationRequest authRequest = new AuthenticationRequest().setUserId(userId);

		if (registrationRequest.getPasswordType() != null)
			return switch (registrationRequest.getPasswordType()) {
				case PASSWORD ->
					this.authenticationService.authenticate(authRequest.setPassword(password), request, response);
				case PIN -> this.authenticationService.authenticate(authRequest.setPin(password), request, response);
				case OTP -> Mono.empty();
			};

		if (!safeIsBlank(registrationRequest.getSocialRegisterState()))
			return this.authenticationService.authenticateWSocial(
					authRequest.setSocialRegisterState(registrationRequest.getSocialRegisterState()), request,
					response);

		return Mono.empty();
	}

	public Mono<ClientRegistrationResponse> registerWSocial(ServerHttpRequest request, ServerHttpResponse response,
			ClientRegistrationRequest registrationRequest) {

		if (safeIsBlank(registrationRequest.getSocialRegisterState()))
			return this.regError("Social register state cannot be blank for social Login.");

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.appRegistrationIntegrationTokenService
						.verifyIntegrationState(registrationRequest.getSocialRegisterState()),

				(ca, appRegIntgToken) -> {
					if (!appRegIntgToken.getUsername().equals(registrationRequest.getUserName())
							&& !appRegIntgToken.getUsername().equals(registrationRequest.getEmailId()))
						return this.regError("Username and EmailId should not be changed");

					return Mono.just(Boolean.TRUE);
				},
				(ca, appRegIntgToken, emailChecked) -> {

					LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);

					if (appRegIntgToken.getCreatedAt().isBefore(twoMinutesAgo))
						return this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								SecurityMessageResourceService.SESSION_EXPIRED);

					return this.register(registrationRequest, request, response);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.registerWSocial"));
	}

	public Mono<String> evokeRegisterWSocial(SecurityAppRegIntegrationPlatform platform,
			ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.appService.getAppByCode(ca.getUrlAppCode()),

				(ca, app) -> this.appRegistrationIntegrationService.getIntegration(platform),

				(ca, app, appRegIntg) -> {

					String state = UUID.randomUUID().toString();

					String host = request.getHeaders().getFirst("X-Forwarded-Host");

					String urlPrefix = "https://" + host;

					String callBackURL = urlPrefix + SOCIAL_CALLBACK_URI;

					return switch (appRegIntg.getPlatform()) {
						case GOOGLE -> this.appRegistrationIntegrationService
								.redirectToGoogleAuthConsent(appRegIntg, state, callBackURL);
						case META -> this.appRegistrationIntegrationService
								.redirectToMetaAuthConsent(appRegIntg, state, callBackURL);
						default -> this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								SecurityMessageResourceService.UNSUPPORTED_PLATFORM);
					};
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.registerWSocial"));
	}

	public Mono<Void> registerWSocialCallback(ServerHttpRequest request, ServerHttpResponse response) {

		String host = request.getHeaders().getFirst("X-Forwarded-Host");

		String urlPrefix = "https://" + host;

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.appService.getAppByCode(ca.getUrlAppCode()),

				(ca, app) -> this.appRegistrationIntegrationTokenService
						.verifyIntegrationState(request.getQueryParams().getFirst("state")),

				(ca, app, appRegIntgToken) -> this.appRegistrationIntegrationService
						.read(appRegIntgToken.getIntegrationId()),

				(ca, app, appRegIntgToken, appRegIntg) -> {

					String callBackURL = urlPrefix + SOCIAL_CALLBACK_URI;

					return switch (appRegIntg.getPlatform()) {
						case GOOGLE -> this.appRegistrationIntegrationService
								.getGoogleUserToken(appRegIntg, appRegIntgToken, callBackURL, request);
						case META -> this.appRegistrationIntegrationService.getMetaUserToken(
								appRegIntg, appRegIntgToken, callBackURL, request);
						default -> this.securityMessageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								SecurityMessageResourceService.UNSUPPORTED_PLATFORM);
					};
				},

				(ca, app, appRegIntgToken, appRegIntg, registerRequest) -> {

					URI redirectUri = UriComponentsBuilder
							.fromUri(URI.create(urlPrefix + appRegIntg.getLoginUri()))
							.queryParam("sessionId", appRegIntgToken.getState())
							.queryParam("userName", registerRequest.getUserName())
							.queryParam("emailId", registerRequest.getEmailId())
							.queryParamIfPresent("phoneNumber", Optional.ofNullable(registerRequest.getPhoneNumber()))
							.queryParamIfPresent("firstName", Optional.ofNullable(registerRequest.getFirstName()))
							.queryParamIfPresent("lastName", Optional.ofNullable(registerRequest.getLastName()))
							.queryParamIfPresent("middleName", Optional.ofNullable(registerRequest.getMiddleName()))
							.queryParamIfPresent("localeCode", Optional.ofNullable(registerRequest.getLocaleCode()))
							.build().toUri();

					response.setStatusCode(HttpStatus.FOUND);
					response.getHeaders().setLocation(redirectUri);
					return response.setComplete();
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.registerWSocialCallback"));
	}

	public Mono<Boolean> evokeRegistrationEvents(ClientRegistrationRequest registrationRequest,
			ServerHttpRequest request, ServerHttpResponse response) {

		String urlPrefix = getUrlPrefix(request);

		AuthenticationPasswordType passType = registrationRequest.getPasswordType();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.userService.getUserForContext(registrationRequest.getUserId()),

				(ca, user) -> this.clientService.getClientInfoById(user.getClientId()),

				(ca, user, client) -> this.getClientAuthenticationResponse(registrationRequest, user.getId(),
						getUserPasswordType((passType), user), request, response),

				(ca, user, client, auth) -> this.clientUrlService.getAppUrl(client.getCode(), ca.getUrlAppCode()),

				(ca, user, client, auth, subDomain) -> this.createRegistrationEvents(ca, client, subDomain, urlPrefix,
						user, auth.getAccessToken(), getUserPasswordType(registrationRequest.getPasswordType(), user))
						.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientService.envokeRegistrationEvents")));
	}

	private String getUserPasswordType(AuthenticationPasswordType passType, User user) {
		return switch (passType) {
			case PASSWORD -> user.getPassword();
			case PIN -> user.getPin();
			default -> null;
		};
	}
}
