package com.fincity.security.service.appregistration;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
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
        extends
        AbstractJOOQUpdatableDataService<SecurityAppRegIntegrationRecord, ULong, AppRegistrationIntegration, AppRegistrationIntegrationDAO> {

    private static final String LOGIN_URI = "loginUri";
    private static final String INTG_ID = "intgId";
    private static final String INTG_SECRET = "intgSecret";

    private static final String CACHE_NAME_INTEGRATION_ID = "integrationId";
    private static final String CACHE_NAME_INTEGRATION_PLATFORM = "integrationPlatform";

    private final AppService appService;
    private final CacheService cacheService;
    private final AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;

    public AppRegistrationIntegrationService(AppService appService, CacheService cacheService,
            AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService) {
        this.appService = appService;
        this.cacheService = cacheService;
        this.appRegistrationIntegrationTokenService = appRegistrationIntegrationTokenService;
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_CREATE')")
    public Mono<AppRegistrationIntegration> create(AppRegistrationIntegration entity) {
        return super.create(entity);
    }

    @Override
    public Mono<AppRegistrationIntegration> read(ULong id) {
        return super.read(id);
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_UPDATE')")
    public Mono<AppRegistrationIntegration> update(AppRegistrationIntegration entity) {

        return super.update(entity)
                .flatMap(
                        this.cacheService.evictFunctionWithKeyFunction(
                                CACHE_NAME_INTEGRATION_ID, this::getCacheKeys));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_DELETE')")
    public Mono<Integer> delete(ULong id) {

        return FlatMapUtil.flatMapMono(
                () -> this.read(id),
                e -> super.delete(id).flatMap(this.cacheService.evictFunction(CACHE_NAME_INTEGRATION_ID,
                        getCacheKeys(e))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "AppRegistrationIntegrationService.delete"));
    }

    @Override
    @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
    public Mono<Page<AppRegistrationIntegration>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    protected Mono<AppRegistrationIntegration> updatableEntity(AppRegistrationIntegration entity) {

        return FlatMapUtil.flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    existing.setIntgId(entity.getIntgId());
                    existing.setIntgSecret(entity.getIntgSecret());
                    existing.setLoginUri(entity.getLoginUri());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "AppRegistrationIntegrationService.updatableEntity"));
    }

    @Override
    protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

        Map<String, Object> newFields = new HashMap<>();

        if (fields.containsKey(LOGIN_URI)) {
            newFields.put(LOGIN_URI, fields.get(LOGIN_URI));
        }
        if (fields.containsKey(INTG_ID)) {
            newFields.put(INTG_ID, fields.get(INTG_ID));
        }
        if (fields.containsKey(INTG_SECRET)) {
            newFields.put(INTG_SECRET, fields.get(INTG_SECRET));
        }

        return Mono.just(newFields);
    }

    public Mono<AppRegistrationIntegration> getIntegration(SecurityAppRegIntegrationPlatform platform) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.getAppByCode(ca.getUrlAppCode()),

                (ca, app) -> this.cacheService.cacheValueOrGet(CACHE_NAME_INTEGRATION_PLATFORM,
                        () -> this.dao.getIntegration(app.getId(),
                                ULong.valueOf(ca.getLoggedInFromClientId()), platform),
                        app.getId(), "-", ca.getLoggedInFromClientId(), "-", platform))
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "AppRegistrationIntegrationService.getIntegration"));
    }

    public Mono<String> redirectToGoogleAuthConsent(AppRegistrationIntegration appRegIntg, String state,
            String callBackURL) {

        return FlatMapUtil.flatMapMono(

                () -> {
                    URI authUri = UriComponentsBuilder
                            .fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                            .queryParam("client_id", appRegIntg.getIntgId())
                            .queryParam("redirect_uri", callBackURL)
                            .queryParam("scope", "email profile openid")
                            .queryParam("response_type", "code")
                            .queryParam("state", state).queryParam("access_type", "offline")
                            .queryParam("prompt", "consent").build().toUri();

                    return Mono.just(authUri);
                },

                authUri -> this.appRegistrationIntegrationTokenService.create(
                        new AppRegistrationIntegrationToken()
                                .setIntegrationId(appRegIntg.getId()).setState(state)),

                (authUri, appRegIntgToken) -> Mono.just(authUri.toString()));
    }

    public Mono<String> redirectToMetaAuthConsent(AppRegistrationIntegration appRegIntg, String state,
            String callBackURL) {

        return FlatMapUtil.flatMapMono(

                () -> {
                    URI authUri = UriComponentsBuilder
                            .fromHttpUrl("https://www.facebook.com/dialog/oauth")
                            .queryParam("client_id", appRegIntg.getIntgId())
                            .queryParam("redirect_uri", callBackURL)
                            .queryParam("scope", "public_profile,email")
                            .queryParam("response_type", "code")
                            .queryParam("state", state).build().toUri();
                    return Mono.just(authUri);
                },

                authUri -> this.appRegistrationIntegrationTokenService.create(
                        new AppRegistrationIntegrationToken()
                                .setIntegrationId(appRegIntg.getId()).setState(state)),

                (authUri, appRegIntgToken) -> Mono.just(authUri.toString()));
    }

    public Mono<ClientRegistrationRequest> getGoogleUserToken(AppRegistrationIntegration appRegIntg,
            AppRegistrationIntegrationToken appRegIntgToken, String callBackURL,
            ServerHttpRequest request) {

        String baseTokenURL = "https://oauth2.googleapis.com/token";
        WebClient webClient = WebClient.create();

        return FlatMapUtil.flatMapMono(

                () -> webClient.post().uri(baseTokenURL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData("client_id", appRegIntg.getIntgId())
                                .with("client_secret", appRegIntg.getIntgSecret())
                                .with("grant_type", "authorization_code")
                                .with("code", request.getQueryParams().getFirst("code"))
                                .with("redirect_uri", callBackURL))
                        .retrieve().bodyToMono(JsonNode.class),

                tokenObj -> this.getGoogleUserInfo(tokenObj.get("access_token").toString()),

                (tokenObj, userObj) -> this.appRegistrationIntegrationTokenService
                        .update(appRegIntgToken
                                .setAuthCode(request.getQueryParams().getFirst("code"))
                                .setToken(tokenObj.get("access_token").asText())
                                .setRefreshToken(tokenObj.get("refresh_token").asText())
                                .setExpiresAt(
                                        LocalDateTime.now().plusSeconds(Long
                                                .parseLong(tokenObj.get(
                                                        "expires_in")
                                                        .asText())))
                                .setUsername(userObj.get("email").asText())
                                .setTokenMetadata(tokenObj)
                                .setUserMetadata(userObj)),

                (tokenObj, userObj, updatedAppRegIntgToken) -> {
                    ClientRegistrationRequest clientRegistrationRequest = new ClientRegistrationRequest();
                    clientRegistrationRequest.setEmailId(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest.setFirstName(userObj.get("given_name").asText());
                    clientRegistrationRequest.setLastName(userObj.get("family_name").asText());
                    clientRegistrationRequest.setUserName(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest
                            .setSocialRegisterState(updatedAppRegIntgToken.getState());

                    return Mono.just(clientRegistrationRequest);
                });
    }

    public Mono<ClientRegistrationRequest> getMetaUserToken(AppRegistrationIntegration appRegIntg,
            AppRegistrationIntegrationToken appRegIntgToken, String callBackURL,
            ServerHttpRequest request) {

        String baseTokenURL = "https://graph.facebook.com/v20.0/oauth/access_token";
        WebClient webClient = WebClient.create();

        return FlatMapUtil.flatMapMono(

                () -> webClient.post().uri(baseTokenURL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData("client_id", appRegIntg.getIntgId())
                                .with("client_secret", appRegIntg.getIntgSecret())
                                .with("grant_type", "authorization_code")
                                .with("code", request.getQueryParams().getFirst("code"))
                                .with("redirect_uri", callBackURL))
                        .retrieve().bodyToMono(JsonNode.class),

                tokenObj -> this.getMetaUserInfo(tokenObj.get("access_token").asText()),

                (tokenObj, userObj) -> this.appRegistrationIntegrationTokenService
                        .update(appRegIntgToken
                                .setAuthCode(request.getQueryParams().getFirst("code"))
                                .setToken(tokenObj.get("access_token").asText())
                                .setExpiresAt(
                                        LocalDateTime.now().plusSeconds(Long
                                                .parseLong(tokenObj.get(
                                                        "expires_in")
                                                        .asText())))
                                .setUsername(userObj.get("email").asText())
                                .setTokenMetadata(tokenObj)
                                .setUserMetadata(userObj)),

                (tokenObj, userObj, updatedAppRegIntgToken) -> {
                    ClientRegistrationRequest clientRegistrationRequest = new ClientRegistrationRequest();
                    clientRegistrationRequest.setEmailId(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest.setFirstName(userObj.get("first_name").asText());
                    clientRegistrationRequest.setLastName(userObj.get("last_name").asText());
                    clientRegistrationRequest.setUserName(updatedAppRegIntgToken.getUsername());
                    clientRegistrationRequest
                            .setSocialRegisterState(updatedAppRegIntgToken.getState());

                    return Mono.just(clientRegistrationRequest);
                });
    }

    private Mono<JsonNode> getGoogleUserInfo(String token) {

        String baseURL = "https://www.googleapis.com/oauth2/v2/userinfo";

        return WebClient.create().get().uri(baseURL).headers(h -> h.setBearerAuth(token)).retrieve()
                .bodyToMono(JsonNode.class);
    }

    private Mono<JsonNode> getMetaUserInfo(String token) {

        String fields = "id,first_name,middle_name,last_name,email,locale";

        return WebClient.create().get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/me")
                        .queryParam("fields", fields)
                        .queryParam("access_token", token)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private String getCacheKeys(AppRegistrationIntegration entity) {
        return String.join(entity.getClientId().toString(), ":", entity.getAppId().toString(), ":",
                entity.getPlatform().toString());
    }

}