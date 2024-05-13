package com.fincity.security.service.appregistration;

import static com.fincity.saas.commons.util.CommonsUtil.nonNullValue;
import static com.fincity.saas.commons.util.StringUtil.safeIsBlank;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.CodeAccessDAO;
import com.fincity.security.dao.appregistration.AppRegistrationDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.feign.IFeignFilesService;
import com.fincity.security.jooq.enums.SecurityAppAppUsageType;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.ClientRegistrationResponse;
import com.fincity.security.service.AppService;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.ClientUrlService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.UserService;
import com.fincity.security.util.PasswordUtil;

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
    private final CodeAccessDAO codeAccessDAO;
    private final AuthenticationService authenticationService;
    private final ClientService clientService;
    private final EventCreationService ecService;
    private final ClientUrlService clientUrlService;
    private final AppRegistrationDAO appRegistrationDAO;
    private final IFeignFilesService filesService;

    private final SecurityMessageResourceService securityMessageResourceService;

    private static final int VALIDITY_MINUTES = 30;

    @Value("${security.subdomain.endings}")
    private String subDomainEndings;

    public ClientRegistrationService(ClientDAO dao, AppService appService, UserService userService,
            SecurityMessageResourceService securityMessageResourceService,
            AuthenticationService authenticationService, CodeAccessDAO codeAccessDAO, ClientService clientService,
            EventCreationService ecService, ClientUrlService clientUrlService, AppRegistrationDAO appRegistrationDAO,
            IFeignFilesService filesService) {
        this.dao = dao;
        this.appService = appService;
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.codeAccessDAO = codeAccessDAO;
        this.clientService = clientService;
        this.ecService = ecService;
        this.clientUrlService = clientUrlService;
        this.appRegistrationDAO = appRegistrationDAO;
        this.filesService = filesService;

        this.securityMessageResourceService = securityMessageResourceService;
    }

    public Mono<Tuple2<String, ContextAuthentication>> preRegisterCheck(ClientRegistrationRequest registrationRequest) {

        if (registrationRequest.isBusinessClient() && StringUtil.safeIsBlank(registrationRequest.getBusinessType()))
            registrationRequest.setBusinessType(AppRegistrationService.DEFAULT_BUSINESS_TYPE);

        Mono<App> appMono = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.getAppByCode(ca.getUrlAppCode()));

        Mono<Client> clientMono = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isAuthenticated()
                        ? this.securityMessageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, "Signout to register")
                        : this.clientService.getClientBy(ca.getLoggedInFromClientCode()));

        Mono<ClientLevelType> clientLevelTypeMono = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> appMono,

                (ca, app) -> {
                    return this.clientService.getClientLevelType(ULong.valueOf(ca.getLoggedInFromClientId()),
                            app.getId());
                });

        Mono<Boolean> checkIfUserExists = FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {
                    if (registrationRequest.isBusinessClient())
                        return Mono.just(true);

                    return this.userService
                            .checkUserExists(ca.getUrlAppCode(), ca.getLoggedInFromClientCode(), registrationRequest)
                            .filter(e -> !e.booleanValue());
                }).switchIfEmpty(this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                        SecurityMessageResourceService.USER_ALREADY_EXISTS, registrationRequest.getEmailId()));

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> checkIfUserExists,

                (ca, exists) -> appMono,

                (ca, exists, app) -> clientMono,

                (ca, exists, app, client) -> clientLevelTypeMono,

                (ca, exists, app, client, levelType) -> this.checkUsageType(app.getAppUsageType(), levelType,
                        registrationRequest.isBusinessClient()),

                (ca, exists, app, client, levelType, usageType) -> this.appService
                        .getProperties(ULong.valueOf(ca.getLoggedInFromClientId()), app.getId(), null,
                                AppService.APP_PROP_URL_SUFFIX)
                        .map(e -> nonNullValue(e.get(ULong.valueOf(ca.getLoggedInFromClientId())),
                                e.get(app.getClientId()), this.subDomainEndings))
                        .defaultIfEmpty(this.subDomainEndings),

                (ca, exists, app, client, levelType, usageType, suffix) -> this.checkSubDomainAvailability(
                        this.subDomainEndings,
                        registrationRequest.getSubDomain(), registrationRequest.isBusinessClient()),

                (ca, exists, app, client, levelType, usageType, suffix, subDomain) -> Mono
                        .just(Tuples.of(subDomain, ca))

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.preRegisterCheck"));
    }

    public Mono<String> checkSubDomainAvailability(String suffix, String subDomain,
            boolean isBusinessClient) {

        if (!isBusinessClient || safeIsBlank(subDomain))
            return Mono.just("");

        String subDomainWithSuffix = subDomain + suffix;

        return this.clientUrlService.checkSubDomainAvailability(subDomainWithSuffix)
                .filter(e -> e)
                .map(e -> subDomainWithSuffix)
                .switchIfEmpty(this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                        SecurityMessageResourceService.SUBDOMAIN_ALREADY_EXISTS, subDomain));
    }

    public Mono<Boolean> checkUsageType(SecurityAppAppUsageType usageType, ClientLevelType levelType,
            boolean isBusinessClient) {

        switch (usageType) {

            case S:
                return this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                        "Not allowed for Standalone Applications");

            case B:
                return this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                        "Not allowed for Business Applications");

            case B2C:
                if (levelType != ClientLevelType.OWNER)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2C Applications");

                if (isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Business clients are not allowed for B2C Applications");

                break;

            case B2B:
                if (levelType != ClientLevelType.OWNER)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2B Applications");

                if (!isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Individual clients are not allowed for B2B Applications");

                break;

            case B2X:
                if (levelType != ClientLevelType.OWNER)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2X Applications");

                break;

            case B2B2B:
                if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2B2B Applications");

                if (!isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Individual clients are not allowed for B2B2B Applications");

                break;

            case B2B2C:
                if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2B2C Applications");

                if (levelType == ClientLevelType.OWNER && !isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Business clients are required for B2B2C Applications at owner level");

                if (levelType == ClientLevelType.CLIENT && isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Business clients are not allowed for B2B2C Applications");

                break;

            case B2B2X:
                if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2B2X Applications");

                if (levelType == ClientLevelType.OWNER && !isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Business clients are required for B2B2X Applications at owner level");

                break;

            case B2X2C:
                if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2X2C Applications");

                if (levelType == ClientLevelType.CLIENT && isBusinessClient)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Business clients are not allowed for B2X2C Applications");

                break;

            case B2X2X:
                if (levelType != ClientLevelType.OWNER && levelType != ClientLevelType.CLIENT)
                    return this.securityMessageResourceService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR,
                            "Only Applications owner can register for B2X2X Applications");

                break;

            default:
                return this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, "Invalid Application Usage Type");
        }

        return Mono.just(true);
    }

    public Mono<ClientRegistrationResponse> register(ClientRegistrationRequest registrationRequest,
            ServerHttpRequest request, ServerHttpResponse response) {

        String host = request.getHeaders().getFirst("X-Forwarded-Host");
        String scheme = request.getHeaders().getFirst("X-Forwarded-Proto");
        String port = request.getHeaders().getFirst("X-Forwarded-Port");

        String urlPrefix = (scheme != null && scheme.contains("https")) ? "https://" + host
                : "http://" + host + ":" + port;

        Mono<ClientRegistrationResponse> mono = FlatMapUtil.flatMapMono(

                () -> this.preRegisterCheck(registrationRequest),

                tup -> {

                    ULong clientId = ULong.valueOf(tup.getT2().getLoggedInFromClientId());

                    return this.appService
                            .getProperties(clientId, null, tup.getT2().getUrlAppCode(), AppService.APP_PROP_REG_TYPE)
                            .map(e -> {
                                if (e.isEmpty())
                                    return "";

                                if (e.containsKey(clientId)
                                        && e.get(clientId).containsKey(AppService.APP_PROP_REG_TYPE))
                                    return e.get(clientId).get(AppService.APP_PROP_REG_TYPE).getValue();

                                var m = e.values().stream().findFirst();
                                if (!m.isPresent())
                                    return "";

                                return m.get().get(AppService.APP_PROP_REG_TYPE).getValue();
                            });
                },

                (tup, prop) -> this.registerClient(registrationRequest, tup.getT2(), prop),

                (tup, prop, client) -> this.registerUser(tup.getT2().getUrlAppCode(),
                        ULong.valueOf(tup.getT2().getLoggedInFromClientId()), registrationRequest, client, prop),

                (tup, prop, client, userTuple) -> {

                    if (!StringUtil.safeIsBlank(registrationRequest.getPassword())) {

                        return this.userService
                                .makeOneTimeToken(request, tup.getT2(), userTuple.getT1(),
                                        ULong.valueOf(tup.getT2().getLoggedInFromClientId()))
                                .map(TokenObject::getToken);
                    }

                    return Mono.just("");
                },

                (tup, prop, client, userTuple, token) -> this.addFilesAccessPath(tup.getT2(), client),

                (tup, prop, client, userTuple, token, filesAccessCreated) -> this.ecService
                        .createEvent(new EventQueObject().setAppCode(tup.getT2().getUrlAppCode())
                                .setClientCode(tup.getT2().getLoggedInFromClientCode())
                                .setEventName(EventNames.CLIENT_REGISTERED)
                                .setData(Map.of("client", client, "subDomain", tup.getT1(), "urlPrefix", urlPrefix)))
                        .flatMap(e -> ecService.createEvent(new EventQueObject().setAppCode(tup.getT2().getUrlAppCode())
                                .setClientCode(tup.getT2().getLoggedInFromClientCode())
                                .setEventName(EventNames.USER_REGISTERED)
                                .setData(Map.of("client", client, "subDomain", tup.getT1(), "user", userTuple.getT1(),
                                        "urlPrefix", urlPrefix,
                                        "token", token,
                                        "passwordUsed", userTuple.getT2()))))
                        .flatMap(e -> {
                            if (AppService.APP_PROP_REG_TYPE_NO_VERIFICATION.equals(prop)
                                    || prop.endsWith("_LOGIN_IMMEDIATE")) {

                                return this.authenticationService
                                        .authenticate(
                                                new AuthenticationRequest()
                                                        .setUserName(CommonsUtil.nonNullValue(
                                                                registrationRequest.getUserName(),
                                                                registrationRequest.getEmailId()))
                                                        .setPassword(registrationRequest.getPassword()),
                                                request, response)
                                        .map(x -> new ClientRegistrationResponse(true, "", x));
                            }

                            return Mono.just(new ClientRegistrationResponse(true, "", null));
                        })
                        .flatMap(e -> {
                            if (prop.equals(AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE)
                                    || prop.equals(AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE_LOGIN_IMMEDIATE)
                                    || prop.equals(AppService.APP_PROP_REG_TYPE_CODE_ON_REQUEST)
                                    || prop.equals(AppService.APP_PROP_REG_TYPE_CODE_ON_REQUEST_LOGIN_IMMEDIATE))

                                this.codeAccessDAO
                                        .deleteRecordAfterRegistration(tup.getT2().getUrlAppCode(),
                                                ULongUtil.valueOf(tup.getT2().getLoggedInFromClientId()),
                                                registrationRequest.getEmailId(), registrationRequest.getCode())
                                        .subscribe();

                            return Mono.just(e);
                        }),

                (tup, prop, client, userTuple, token, filesAccessCreated, res) -> {

                    if (tup.getT1().isBlank())
                        return Mono.just(res);

                    return this.clientUrlService
                            .createForRegistration(new ClientUrl().setAppCode(tup.getT2().getUrlAppCode())
                                    .setUrlPattern(tup.getT1()).setClientId(client.getId()))
                            .map(e -> res.setRedirectURL(tup.getT1()));
                });
        return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.register"));
    }

    private Mono<Boolean> addFilesAccessPath(ContextAuthentication ca, Client client) {

        return FlatMapUtil.flatMapMono(

                () -> this.appService.getAppByCode(ca.getUrlAppCode()),

                app -> this.clientService.getClientLevelType(client.getId(), app.getId()),

                (app, levelType) -> this.appRegistrationDAO.getFileAccessForRegistration(app.getId(), app.getClientId(),
                        ULong.valueOf(ca.getLoggedInFromClientId()), client.getTypeCode(), levelType,
                        client.getBusinessType()),

                (app, levelType, filesAccess) -> Flux.fromIterable(filesAccess)
                        .map(e -> {
                            IFeignFilesService.FilesAccessPath accessPath = new IFeignFilesService.FilesAccessPath();
                            accessPath.setClientCode(client.getCode());
                            accessPath.setAccessName(e.getAccessName());
                            accessPath.setWriteAccess(e.isWriteAccess());
                            accessPath.setPath(e.getPath());
                            accessPath.setAllowSubPathAccess(e.isAllowSubPathAccess());
                            accessPath.setResourceType(e.getResourceType());
                            return accessPath;
                        })
                        .flatMap(filesService::createInternalAccessPath)
                        .collectList().map(e -> true)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.addFilesAccessPath"));
    }

    private Mono<Tuple2<User, String>> registerUser(String appCode, ULong urlClientId,
            ClientRegistrationRequest request, Client client, String regType) {
        User user = new User();
        user.setClientId(client.getId());
        user.setEmailId(request.getEmailId());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setLocaleCode(request.getLocaleCode());
        user.setUserName(request.getUserName());

        String password = "";
        if (regType.equals(AppService.APP_PROP_REG_TYPE_EMAIL_PASSWORD)) {

            password = PasswordUtil.generatePassword(8);
            user.setPassword(password);
            user.setStatusCode(SecurityUserStatusCode.ACTIVE);
        } else if (StringUtil.safeIsBlank(request.getPassword())) {

            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.MISSING_PASSWORD);

        } else if (regType.equals(AppService.APP_PROP_REG_TYPE_EMAIL_VERIFY)) {
            user.setPassword(request.getPassword());
            user.setStatusCode(SecurityUserStatusCode.INACTIVE);
        } else {
            // In all other cases we make the user active as the user will already be
            // authenticated by a code or no verification required.

            user.setPassword(request.getPassword());
            user.setStatusCode(SecurityUserStatusCode.ACTIVE);
        }

        final String finPassword = password;

        return this.appService.getAppByCode(appCode)
                .flatMap(app -> this.userService
                        .createForRegistration(app.getId(), app.getClientId(), urlClientId, client, user)
                        .map(e -> Tuples.of(e, finPassword)));
    }

    private String getValidClientName(ClientRegistrationRequest request) {

        if (!StringUtil.safeIsBlank(request.getClientName()))
            return request.getClientName();
        if (!StringUtil.safeIsBlank(request.getFirstName()) || !StringUtil.safeIsBlank(request.getLastName()))
            return (StringUtil.safeValueOf(request.getFirstName(), "")
                    + StringUtil.safeValueOf(request.getLastName(), ""));
        if (!StringUtil.safeIsBlank(request.getEmailId()))
            return request.getEmailId();
        if (!StringUtil.safeIsBlank(request.getUserName()))
            return request.getUserName();

        return "";
    }

    private Mono<Client> registerClient(ClientRegistrationRequest request, ContextAuthentication ca, String regType) {

        if (StringUtil.safeIsBlank(regType) || AppService.APP_PROP_REG_TYPE_NO_REGISTRATION.equals(regType)) {
            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.NO_REGISTRATION_AVAILABLE);
        }

        if (ca.isAuthenticated())
            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, "Signout to register");

        Client client = new Client();

        String clientName = getValidClientName(request);

        if (StringUtil.safeIsBlank(clientName))
            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.FIELDS_MISSING);

        client.setName(clientName);
        client.setTypeCode(request.isBusinessClient() ? "BUS" : "INDV");
        client.setLocaleCode(request.getLocaleCode());
        client.setTokenValidityMinutes(VALIDITY_MINUTES);

        if (StringUtil.safeIsBlank(client.getName()))
            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CLIENT_REGISTRATION_ERROR, "Client name cannot be blank");

        return FlatMapUtil.flatMapMono(

                () -> this.appService.getAppByCode(ca.getUrlAppCode()),

                app -> {

                    if (regType.equals(AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE)
                            || regType.equals(AppService.APP_PROP_REG_TYPE_CODE_IMMEDIATE_LOGIN_IMMEDIATE)
                            || regType.equals(AppService.APP_PROP_REG_TYPE_CODE_ON_REQUEST)
                            || regType.equals(AppService.APP_PROP_REG_TYPE_CODE_ON_REQUEST_LOGIN_IMMEDIATE))

                        return this.codeAccessDAO
                                .checkClientAccessCode(
                                        app.getId(), ULongUtil.valueOf(ca.getLoggedInFromClientId()), request
                                                .getEmailId(),
                                        request.getCode())
                                .flatMap(e -> Mono.justOrEmpty(e.booleanValue() ? true : null));

                    return Mono.just(true);

                },

                (app, validReg) -> this.dao.getValidClientCode(client.getName())
                        .map(client::setCode),

                (app, validReg, c) -> this.clientService.createForRegistration(c),

                (app, validReg, c, clnt) -> this.dao.addManageRecord(ca.getLoggedInFromClientCode(),
                        clnt.getId()),

                (app, validReg, c, clnt, num) -> this.clientService.addClientPackagesAfterRegistration(
                        app.getId(),
                        app.getClientId(),
                        ULong.valueOf(ca.getLoggedInFromClientId()),
                        clnt),

                (app, validReg, c, clnt, num, packagesAdded) -> this.appService.addClientAccessAfterRegistration(
                        ca.getUrlAppCode(),
                        ULong.valueOf(ca.getLoggedInFromClientId()),
                        clnt),

                (app, validReg, c, clnt, num, packagesAdded, clientAccessAdded) -> Mono.just(clnt)

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientRegistrationService.registerClient"))
                .switchIfEmpty(this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.ACCESS_CODE_INCORRECT));
    }
}