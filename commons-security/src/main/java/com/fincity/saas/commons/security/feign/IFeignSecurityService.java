package com.fincity.saas.commons.security.feign;

import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@reactivefeign.spring.config.ReactiveFeignClient(name = "security")
public interface IFeignSecurityService {

    @GetMapping("${security.feign.contextAuthentication:/api/security/internal/securityContextAuthentication}")
    Mono<ContextAuthentication> contextAuthentication(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String appCode);

    @GetMapping("${security.feign.isBeingManaged:/api/security/clients/internal/isBeingManaged}")
    Mono<Boolean> isBeingManaged(@RequestParam String managingClientCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.isBeingManagedById:/api/security/clients/internal/isBeingManagedById}")
    Mono<Boolean> isBeingManagedById(@RequestParam BigInteger managingClientId, @RequestParam BigInteger clientId);

    @GetMapping("${security.feign.getClientById:/api/security/clients/internal/getClientById}")
    Mono<Client> getClientById(@RequestParam BigInteger clientId);

    @GetMapping("${security.feign.getClientByCode:/api/security/clients/internal/getClientByCode}")
    Mono<Client> getClientByCode(@RequestParam String clientCode);

    @GetMapping("${security.feign.isUserBeingManaged:/api/security/clients/internal/isUserBeingManaged}")
    Mono<Boolean> isUserBeingManaged(@RequestParam BigInteger userId, @RequestParam String clientCode);

    @GetMapping("${security.feign.hasReadAccess:/api/security/applications/internal/hasReadAccess}")
    Mono<Boolean> hasReadAccess(@RequestParam String appCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.hasWriteAccess:/api/security/applications/internal/hasWriteAccess}")
    Mono<Boolean> hasWriteAccess(@RequestParam String appCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.validClientCode:/api/security/clients/internal/validateClientCode}")
    Mono<Boolean> validClientCode(@RequestParam String clientCode);

    @GetMapping("${security.feign.hasWriteAccess:/api/security/applications/internal/appInheritance}")
    Mono<List<String>> appInheritance(
            @RequestParam String appCode, @RequestParam String urlClientCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.token:/api/security/ssl/token/{token}}")
    Mono<String> token(@PathVariable("token") String token);

    @GetMapping("${security.feign.getAppByCode:/api/security/applications/internal/appCode/{appCode}}")
    Mono<App> getAppByCode(@PathVariable("appCode") String appCode);

    @GetMapping("${security.feign.getAppByCode:/api/security/applications/internal/explicitInfo/{appCode}}")
    Mono<App> getAppExplicitInfoByCode(@PathVariable("appCode") String appCode);

    @GetMapping("${security.feign.getAppById:/api/security/applications/{id}}")
    Mono<App> getAppById(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("id") String id);

    @DeleteMapping("${security.feign.deleteByAppId:/api/security/applications/{id}}")
    Mono<Boolean> deleteByAppId(
            @RequestHeader(name = "Authorization") String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("id") BigInteger id);

    @GetMapping("${security.feign.transport:/api/security/transports/makeTransport}")
    Mono<Map<String, Object>> makeTransport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestParam("applicationCode") String applicationCode);

    @PostMapping("${security.feign.createApp:/api/security/applications/}")
    Mono<App> createApp(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestBody App application);

    @PostMapping("${security.feign.transportApply:/api/security/transports/createAndApply}")
    Mono<Boolean> createAndApplyTransport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestBody Object securityDefinition);

    @GetMapping(
            "${security.feign.findBaseClientCodeForOverride:/api/security/applications/findBaseClientCode/{applicationCode}}")
    Mono<Tuple2<String, Boolean>> findBaseClientCodeForOverride(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("applicationCode") String applicationCode);

    @GetMapping("${security.feign.dependencies:/api/security/applications/internal/dependencies}")
    Mono<List<String>> getDependencies(@RequestParam String appCode);

    @GetMapping("${security.feign.getAppUrl:/api/security/clienturls/internal/applications/property/url}")
    Mono<String> getAppUrl(@RequestParam String appCode, @RequestParam(required = false) String clientCode);

    @DeleteMapping("${security.feign.deleteEveryting:/api/security/applications/{id}}")
    Mono<Boolean> deleteEverything(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("id") final Long id);

    @GetMapping("${security.feign.hasDeleteAccess:/api/security/applications/hasDeleteAccess}")
    Mono<Boolean> hasDeleteAccess(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String headerClientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestParam("deleteAppCode") String deleteAppCode,
            @RequestParam("deleteClientCode") String deleteClientCode);

    @GetMapping(
            value =
                    "${security.feign.authenticateWithOneTimeToken:/api/security/authenticateWithOneTimeToken/{pathToken}}")
    Mono<ContextAuthentication> authenticateWithOneTimeToken(
            @PathVariable("pathToken") String pathToken,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader(name = "clientCode", required = false) String clientCode,
            @RequestHeader(name = "appCode", required = false) String headerAppCode,
            @RequestHeader("X-Real-IP") String ipAddress);

    @GetMapping(value = "${security.feign.getUser:/api/security/users/internal/{id}}")
    Mono<Map<String, Object>> getUserInternal(@PathVariable("id") BigInteger id);

    @GetMapping(value = "${security.feign.getUser:/api/security/users/internal}")
    Mono<List<Map<String, Object>>> getUserInternal(@RequestParam List<BigInteger> userIds);

    @GetMapping(value = "${security.feign.getProfileUsers:/api/security/app/profiles/internal/users}")
    Mono<List<BigInteger>> getProfileUsers(
            @RequestHeader("appCode") String headerAppCode, @RequestParam List<BigInteger> profileIds);

    @GetMapping(value = "${security.feign.getUserSubOrgInternal:/api/security/users/internal/{userId}/sub-org}")
    Mono<List<BigInteger>> getUserSubOrgInternal(
            @PathVariable BigInteger userId, @RequestParam String appCode, @RequestParam BigInteger clientId);

    @GetMapping(value = "${security.feign.getUserAdminEmails:/api/security/users/internal/adminEmails}")
    Mono<Map<String,Object>> getUserAdminEmailsInternal(
            @RequestHeader(name = "clientCode") String clientCode,
            @RequestHeader(name = "appCode") String headerAppCode);
}
