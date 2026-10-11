package com.fincity.saas.commons.core.feign;

import java.math.BigInteger;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.JsonNode;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

/**
 * Security's PUBLIC user, client, client-manager, client-activity and profile routes, called AS
 * THE CALLER: every method forwards the caller's own {@code Authorization} plus the
 * {@code X-Forwarded-Host} / {@code X-Forwarded-Port} / {@code clientCode} / {@code appCode} headers
 * of the request that started the function, so security authenticates the same token the page
 * would have sent and applies its own {@code @PreAuthorize} and client checks unchanged.
 *
 * <p>Only for {@code CoreServices.Security.*} user and org administration (see
 * {@code CallerSecurityFunctions}). Nothing here goes to an {@code /internal} route: if there is no
 * caller token to forward, the function refuses instead of calling anything.
 *
 * <p>Responses are taken as raw JSON text and handed to KIRun as is, so a function answers exactly
 * the body the page used to read from the REST call. Bodies go out as Jackson trees for the same
 * reason.
 */
@ReactiveFeignClient(name = "security", qualifier = "coreSecurityAsCaller")
public interface IFeignSecurityAsCaller {

    String AUTHORIZATION = "Authorization";
    String FORWARDED_HOST = "X-Forwarded-Host";
    String FORWARDED_PORT = "X-Forwarded-Port";
    String CLIENT_CODE = "clientCode";
    String APP_CODE = "appCode";

    String USERS = "/api/security/users";
    String CLIENTS = "/api/security/clients";
    String CLIENT_MANAGERS = "/api/security/client-managers";
    String CLIENT_ACTIVITIES = "/api/security/client-activities";

    // ---------------------------------------------------------------- users

    /** {@code POST /users/query?fetchProfiles&fetchClients&...}: one page, scoped by security to the caller. */
    @PostMapping(value = USERS + "/query", consumes = "application/json")
    Mono<String> queryUsers(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestParam MultiValueMap<String, String> params,
            @RequestBody JsonNode query);

    @GetMapping(USERS + "/{id}")
    Mono<String> getUser(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id);

    @PostMapping(value = USERS, consumes = "application/json")
    Mono<String> createUser(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestBody JsonNode user);

    /** {@code PATCH /users/{id}}: only the fields sent change. */
    @PatchMapping(value = USERS + "/{id}", consumes = "application/json")
    Mono<String> patchUser(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id,
            @RequestBody JsonNode fields);

    /** {@code PUT /users/{id}}: the whole user, as the partner pages send it. */
    @PutMapping(value = USERS + "/{id}", consumes = "application/json")
    Mono<String> putUser(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id,
            @RequestBody JsonNode user);

    @DeleteMapping(USERS + "/{id}")
    Mono<String> deleteUser(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id);

    @PatchMapping(USERS + "/makeUserActive")
    Mono<String> makeUserActive(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestParam("userId") BigInteger userId);

    @PatchMapping(USERS + "/makeUserInActive")
    Mono<String> makeUserInActive(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestParam("userId") BigInteger userId);

    @GetMapping(USERS + "/{userId}/assignProfile/{profileId}")
    Mono<String> assignProfile(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("userId") BigInteger userId,
            @PathVariable("profileId") BigInteger profileId);

    @GetMapping(USERS + "/{userId}/removeProfile/{profileId}")
    Mono<String> removeProfile(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("userId") BigInteger userId,
            @PathVariable("profileId") BigInteger profileId);

    // -------------------------------------------------------------- clients

    @GetMapping(CLIENTS + "/makeClientActive")
    Mono<String> makeClientActive(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestParam("clientId") BigInteger clientId);

    @GetMapping(CLIENTS + "/makeClientInActive")
    Mono<String> makeClientInActive(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestParam("clientId") BigInteger clientId);

    @PatchMapping(value = CLIENTS + "/{id}", consumes = "application/json")
    Mono<String> patchClient(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id,
            @RequestBody JsonNode fields);

    @PutMapping(value = CLIENTS + "/{id}", consumes = "application/json")
    Mono<String> putClient(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id,
            @RequestBody JsonNode client);

    @DeleteMapping(CLIENTS + "/{id}")
    Mono<String> deleteClient(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("id") BigInteger id);

    // ------------------------------------------------------ client managers

    @PostMapping(CLIENT_MANAGERS + "/{userId}/{clientId}")
    Mono<String> addClientManager(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("userId") BigInteger userId,
            @PathVariable("clientId") BigInteger clientId);

    @DeleteMapping(CLIENT_MANAGERS + "/{userId}/{clientId}")
    Mono<String> removeClientManager(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("userId") BigInteger userId,
            @PathVariable("clientId") BigInteger clientId);

    /** {@code POST /client-managers/{clientId}?oldManagerId&newManagerId}: replace one manager (or add one). */
    @PostMapping(CLIENT_MANAGERS + "/{clientId}")
    Mono<String> replaceClientManager(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("clientId") BigInteger clientId,
            @RequestParam MultiValueMap<String, String> params);

    /** {@code PUT /client-managers/{clientId}/managers}: the client's managers become exactly this list. */
    @PutMapping(value = CLIENT_MANAGERS + "/{clientId}/managers", consumes = "application/json")
    Mono<String> setClientManagers(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("clientId") BigInteger clientId,
            @RequestBody JsonNode managerIds);

    /** {@code POST /client-managers/migrateTo/{toUserId}?fromUid&removeManager}: answers the count moved. */
    @PostMapping(CLIENT_MANAGERS + "/migrateTo/{toUserId}")
    Mono<String> migrateClientManagers(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("toUserId") BigInteger toUserId,
            @RequestParam("fromUid") BigInteger fromUserId,
            @RequestParam("removeManager") boolean removeManager);

    // ---------------------------------------------------- client activities

    @PostMapping(value = CLIENT_ACTIVITIES, consumes = "application/json")
    Mono<String> createClientActivity(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @RequestBody JsonNode activity);

    @PostMapping(value = CLIENT_ACTIVITIES + "/{clientId}/query", consumes = "application/json")
    Mono<String> queryClientActivities(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("clientId") BigInteger clientId,
            @RequestBody JsonNode query);

    // ------------------------------------------------------- apps, profiles

    /** {@code GET /applications/appCode/{appCode}}: the app as the caller may see it. */
    @GetMapping("/api/security/applications/appCode/{code}")
    Mono<String> getAppByCode(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("code") String code);

    /** {@code GET /app/{appId}/profiles?page&size&sort}: the profiles of an app the caller may see. */
    @GetMapping("/api/security/app/{appId}/profiles")
    Mono<String> getAppProfiles(
            @RequestHeader(AUTHORIZATION) String authorization,
            @RequestHeader(FORWARDED_HOST) String forwardedHost,
            @RequestHeader(FORWARDED_PORT) String forwardedPort,
            @RequestHeader(CLIENT_CODE) String clientCode,
            @RequestHeader(APP_CODE) String appCode,
            @PathVariable("appId") BigInteger appId,
            @RequestParam MultiValueMap<String, String> params);
}
