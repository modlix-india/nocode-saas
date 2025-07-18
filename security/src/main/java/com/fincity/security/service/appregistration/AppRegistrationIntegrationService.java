package com.fincity.security.service.appregistration;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.security.service.SecurityMessageResourceService;
import org.jooq.types.ULong;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.AppRegistrationIntegrationDAO;
import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationRecord;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.service.AppService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AppRegistrationIntegrationService
        extends AbstractJOOQUpdatableDataService<
                SecurityAppRegIntegrationRecord, ULong, AppRegistrationIntegration, AppRegistrationIntegrationDAO> {

    private static final String REDIRECT_URI = "redirect_uri";
    private static final String CLIENT_ID = "client_id";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String LOGIN_URI = "loginUri";
    private static final String INTG_ID = "intgId";
    private static final String INTG_SECRET = "intgSecret";

    private static final String CACHE_NAME_INTEGRATION_PLATFORM = "integrationPlatform";

    private final AppService appService;
    private final CacheService cacheService;
    private final AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;
    private final SecurityMessageResourceService securityMessageResourceService;

    public AppRegistrationIntegrationService(
            AppService appService,
            CacheService cacheService,
            AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService,
            SecurityMessageResourceService securityMessageResourceService) {
        this.appService = appService;
        this.cacheService = cacheService;
        this.appRegistrationIntegrationTokenService = appRegistrationIntegrationTokenService;
        this.securityMessageResourceService = securityMessageResourceService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_CREATE')")
    public Mono<AppRegistrationIntegration> create(AppRegistrationIntegration entity) {
        return super.create(entity);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_UPDATE')")
    public Mono<AppRegistrationIntegration> update(AppRegistrationIntegration entity) {

        return super.update(entity).flatMap(e -> this.cacheService
                .evict(
                        CACHE_NAME_INTEGRATION_PLATFORM,
                        getCacheKeys(
                                e.getClientId().toString(),
                                e.getAppId().toString(),
                                e.getPlatform().toString()))
                .map(v -> e));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_DELETE')")
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(() -> this.read(id), e -> super.delete(id)
                        .flatMap(this.cacheService.evictFunction(
                                CACHE_NAME_INTEGRATION_PLATFORM,
                                getCacheKeys(
                                        e.getClientId().toString(),
                                        e.getAppId().toString(),
                                        e.getPlatform().toString()))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationIntegrationService.delete"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
    public Mono<Page<AppRegistrationIntegration>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    @Override
    protected Mono<AppRegistrationIntegration> updatableEntity(AppRegistrationIntegration entity) {

        return FlatMapUtil.flatMapMono(() -> this.read(entity.getId()), existing -> {
                    existing.setIntgId(entity.getIntgId());
                    existing.setIntgSecret(entity.getIntgSecret());
                    existing.setLoginUri(entity.getLoginUri());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationIntegrationService.updatableEntity"));
    }

    public Mono<AppRegistrationIntegration> getIntegration(SecurityAppRegIntegrationPlatform platform) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.appService.getAppByCode(ca.getUrlAppCode()),

                        (ca, app) -> this.cacheService.cacheValueOrGet(
                                CACHE_NAME_INTEGRATION_PLATFORM,
                                () -> this.dao.getIntegration(
                                        app.getId(), ULong.valueOf(ca.getLoggedInFromClientId()), platform),
                                getCacheKeys(
                                        ca.getLoggedInFromClientId().toString(),
                                        app.getId().toString(),
                                        platform.toString())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppRegistrationIntegrationService.getIntegration"));
    }

    public Mono<String> redirectToGoogleAuthConsent(AppRegistrationIntegration appRegIntg, String state,
                                                    String callBackURL, ServerHttpRequest request) {

        Map<String, Object> params = Collections.unmodifiableMap(request.getQueryParams().toSingleValueMap());

        return FlatMapUtil.flatMapMono(

                () -> {
                    URI authUri = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                            .queryParam(CLIENT_ID, appRegIntg.getIntgId())
                            .queryParam(REDIRECT_URI, callBackURL)
                            .queryParam("scope", "email profile openid")
                            .queryParam("response_type", "code")
                            .queryParam("state", state).queryParam("access_type", "offline")
                            .queryParam("prompt", "consent").build().toUri();

                    return Mono.just(authUri);
                },

                authUri -> this.appRegistrationIntegrationTokenService.create(
                        new AppRegistrationIntegrationToken()
                                .setIntegrationId(appRegIntg.getId()).setState(state).setRequestParam(params)),

                (authUri, appRegIntgToken) -> Mono.just(authUri.toString()));
    }

    public Mono<String> redirectToMetaAuthConsent(AppRegistrationIntegration appRegIntg, String state,
                                                  String callBackURL, ServerHttpRequest request) {

        Map<String, Object> params = Collections.unmodifiableMap(request.getQueryParams().toSingleValueMap());

        return FlatMapUtil.flatMapMono(

                () -> {
                    URI authUri = UriComponentsBuilder.fromUriString("https://www.facebook.com/dialog/oauth")
                            .queryParam(CLIENT_ID, appRegIntg.getIntgId())
                            .queryParam(REDIRECT_URI, callBackURL)
                            .queryParam("scope", "public_profile,email")
                            .queryParam("response_type", "code")
                            .queryParam("state", state).build().toUri();
                    return Mono.just(authUri);
                },

                authUri -> this.appRegistrationIntegrationTokenService.create(
                        new AppRegistrationIntegrationToken()
                                .setIntegrationId(appRegIntg.getId()).setState(state).setRequestParam(params)),

                (authUri, appRegIntgToken) -> Mono.just(authUri.toString()));
    }

    public Mono<ClientRegistrationRequest> getGoogleUserToken(
            AppRegistrationIntegration appRegIntg,
            AppRegistrationIntegrationToken appRegIntgToken,
            String callBackURL,
            ServerHttpRequest request) {

        String baseTokenURL = "https://oauth2.googleapis.com/token";
        WebClient webClient = WebClient.create();

        String authCode = request.getQueryParams().getFirst("code");

        if (authCode == null) {
            return Mono.error(new GenericException(
                    HttpStatus.UNAUTHORIZED,
                    securityMessageResourceService.getDefaultLocaleMessage(SecurityMessageResourceService.SOCIAL_LOGIN_FAILED)));
        }

        return FlatMapUtil.flatMapMono(

                () -> webClient.post().uri(baseTokenURL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(CLIENT_ID, appRegIntg.getIntgId())
                                .with("client_secret", appRegIntg.getIntgSecret())
                                .with("grant_type", "authorization_code")
                                .with("code", authCode)
                                .with(REDIRECT_URI, callBackURL))
                        .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}),

                tokenObj -> this.getGoogleUserInfo(tokenObj.get(ACCESS_TOKEN).toString()),

                (tokenObj, userObj) -> this.appRegistrationIntegrationTokenService
                        .update(appRegIntgToken
                                .setAuthCode(request.getQueryParams().getFirst("code"))
                                .setToken(tokenObj.getOrDefault(ACCESS_TOKEN, "").toString())
                                .setRefreshToken(tokenObj.get("refresh_token").toString())
                                .setExpiresAt(
                                        LocalDateTime.now().plusSeconds(Long
                                                .parseLong(tokenObj.get(
                                                                "expires_in").toString())))
                                .setUsername(userObj.getOrDefault("email", "").toString())
                                .setTokenMetadata(tokenObj)
                                .setUserMetadata(userObj)),

                (tokenObj, userObj, updatedAppRegIntgToken) -> {
                    ClientRegistrationRequest clientRegistrationRequest = new ClientRegistrationRequest();
                    clientRegistrationRequest.setEmailId(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest.setFirstName(userObj.getOrDefault("given_name", "").toString());
                    if (userObj.containsKey("family_name") && userObj.get("family_name") != null) {
                        clientRegistrationRequest.setLastName(userObj.get("family_name").toString());
                    }
                    clientRegistrationRequest.setUserName(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest
                            .setSocialRegisterState(updatedAppRegIntgToken.getState());

                    return Mono.just(clientRegistrationRequest);
                });
    }

    public Mono<ClientRegistrationRequest> getMetaUserToken(
            AppRegistrationIntegration appRegIntg,
            AppRegistrationIntegrationToken appRegIntgToken,
            String callBackURL,
            ServerHttpRequest request) {

        String baseTokenURL = "https://graph.facebook.com/v20.0/oauth/access_token";
        WebClient webClient = WebClient.create();

        String authCode = request.getQueryParams().getFirst("code");

        if (authCode == null) {
            return Mono.error(new GenericException(
                    HttpStatus.UNAUTHORIZED,
                    securityMessageResourceService.getDefaultLocaleMessage(SecurityMessageResourceService.SOCIAL_LOGIN_FAILED)));
        }

        return FlatMapUtil.flatMapMono(

                () -> webClient.post().uri(baseTokenURL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(CLIENT_ID, appRegIntg.getIntgId())
                                .with("client_secret", appRegIntg.getIntgSecret())
                                .with("grant_type", "authorization_code")
                                .with("code", authCode)
                                .with(REDIRECT_URI, callBackURL))
                        .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}),

                tokenObj -> this.getMetaUserInfo(tokenObj.getOrDefault(ACCESS_TOKEN, null).toString()),

                (tokenObj, userObj) -> this.appRegistrationIntegrationTokenService
                        .update(appRegIntgToken
                                .setAuthCode(request.getQueryParams().getFirst("code"))
                                .setToken(tokenObj.get(ACCESS_TOKEN).toString())
                                .setExpiresAt(
                                        LocalDateTime.now().plusSeconds(Long
                                                .parseLong(tokenObj.get(
                                                                "expires_in")
                                                        .toString())))
                                .setUsername(userObj.getOrDefault("email", "").toString())
                                .setTokenMetadata(tokenObj)
                                .setUserMetadata(userObj)),

                (tokenObj, userObj, updatedAppRegIntgToken) -> {
                    ClientRegistrationRequest clientRegistrationRequest = new ClientRegistrationRequest();
                    clientRegistrationRequest.setEmailId(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest.setFirstName(userObj.getOrDefault("first_name", "").toString());
                    clientRegistrationRequest.setLastName(userObj.getOrDefault("last_name", "").toString());
                    clientRegistrationRequest.setUserName(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest
                            .setSocialRegisterState(updatedAppRegIntgToken.getState());

                    return Mono.just(clientRegistrationRequest);
                });
    }

    private Mono<Map<String, Object>> getGoogleUserInfo(String token) {

        String baseURL = "https://www.googleapis.com/oauth2/v2/userinfo";

        return WebClient.create().get().uri(baseURL).headers(h -> h.setBearerAuth(token)).retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    private Mono<Map<String, Object>> getMetaUserInfo(String token) {

        String fields = "id,first_name,middle_name,last_name,email,locale";

        return WebClient.create().get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/me")
                        .queryParam("fields", fields)
                        .queryParam(ACCESS_TOKEN, token)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    private String getCacheKeys(String clientId, String appId, String platform) {
        return String.join(clientId, ":", appId, ":", platform);
    }

}