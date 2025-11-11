package com.modlix.saas.commons2.security.feign;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.modlix.saas.commons2.model.Query;
import com.modlix.saas.commons2.security.dto.App;
import com.modlix.saas.commons2.security.dto.Client;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.model.EntityProcessorUser;
import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.security.model.User;
import com.modlix.saas.commons2.security.model.UsersListRequest;
import com.modlix.saas.commons2.util.Tuples.Tuple2;

@FeignClient(name = "security")
public interface IFeignSecurityService {

    @GetMapping("${security.feign.contextAuthentication:/api/security/internal/securityContextAuthentication}")
    ContextAuthentication contextAuthentication(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String appCode);

    @GetMapping("${security.feign.isBeingManaged:/api/security/clients/internal/isBeingManaged}")
    Boolean isBeingManaged(@RequestParam String managingClientCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.isBeingManagedById:/api/security/clients/internal/isBeingManagedById}")
    Boolean isBeingManagedById(@RequestParam BigInteger managingClientId, @RequestParam BigInteger clientId);

    @GetMapping("${security.feign.getClientById:/api/security/clients/internal/getClientById}")
    Client getClientById(@RequestParam BigInteger clientId);

    @GetMapping("${security.feign.getClientByCode:/api/security/clients/internal/getClientByCode}")
    Client getClientByCode(@RequestParam String clientCode);

    @GetMapping("${security.feign.getManagedClientOfClientById:/api/security/clients/internal/managedClient}")
    Client getManagedClientOfClientById(@RequestParam BigInteger clientId);

    @GetMapping("${security.feign.isUserBeingManaged:/api/security/clients/internal/isUserBeingManaged}")
    Boolean isUserBeingManaged(@RequestParam BigInteger userId, @RequestParam String clientCode);

    @GetMapping("${security.feign.getClientHierarchy:/api/security/clients/internal/clientHierarchy}")
    List<BigInteger> getClientHierarchy(@RequestParam BigInteger clientId);

    @GetMapping("${security.feign.getManagingClientIds:/api/security/clients/internal/managingClientIds}")
    List<BigInteger> getManagingClientIds(@RequestParam BigInteger clientId);

    @GetMapping("${security.feign.hasReadAccess:/api/security/applications/internal/hasReadAccess}")
    Boolean hasReadAccess(@RequestParam String appCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.hasWriteAccess:/api/security/applications/internal/hasWriteAccess}")
    Boolean hasWriteAccess(@RequestParam String appCode, @RequestParam String clientCode);

    @GetMapping("${security.feign.validClientCode:/api/security/clients/internal/validateClientCode}")
    Boolean validClientCode(@RequestParam String clientCode);

    @GetMapping("${security.feign.hasWriteAccess:/api/security/applications/internal/appInheritance}")
    List<String> appInheritance(
            @RequestParam String appCode, @RequestParam String urlClientCode,
            @RequestParam String clientCode);

    @GetMapping("${security.feign.token:/api/security/ssl/token/{token}}")
    String token(@PathVariable("token") String token);

    @GetMapping("${security.feign.getAppByCode:/api/security/applications/internal/appCode/{appCode}}")
    App getAppByCode(@PathVariable("appCode") String appCode);

    @GetMapping("${security.feign.getAppByCode:/api/security/applications/internal/explicitInfo/{appCode}}")
    App getAppExplicitInfoByCode(@PathVariable("appCode") String appCode);

    @GetMapping("${security.feign.getAppById:/api/security/applications/{id}}")
    App getAppById(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("id") String id);

    @DeleteMapping("${security.feign.deleteByAppId:/api/security/applications/{id}}")
    Boolean deleteByAppId(
            @RequestHeader(name = "Authorization") String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("id") BigInteger id);

    @GetMapping("${security.feign.transport:/api/security/transports/makeTransport}")
    Map<String, Object> makeTransport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestParam("applicationCode") String applicationCode);

    @PostMapping("${security.feign.createApp:/api/security/applications/}")
    App createApp(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestBody App application);

    @PostMapping("${security.feign.transportApply:/api/security/transports/createAndApply}")
    Boolean createAndApplyTransport(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestBody Object securityDefinition);

    @GetMapping("${security.feign.findBaseClientCodeForOverride:/api/security/applications/findBaseClientCode/{applicationCode}}")
    Tuple2<String, Boolean> findBaseClientCodeForOverride(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("applicationCode") String applicationCode);

    @GetMapping("${security.feign.dependencies:/api/security/applications/internal/dependencies}")
    List<String> getDependencies(@RequestParam String appCode);

    @GetMapping("${security.feign.getAppUrl:/api/security/clienturls/internal/applications/property/url}")
    String getAppUrl(@RequestParam String appCode, @RequestParam(required = false) String clientCode);

    @DeleteMapping("${security.feign.deleteEveryting:/api/security/applications/{id}}")
    Boolean deleteEverything(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @PathVariable("id") final Long id);

    @GetMapping("${security.feign.hasDeleteAccess:/api/security/applications/hasDeleteAccess}")
    Boolean hasDeleteAccess(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String headerClientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestParam("deleteAppCode") String deleteAppCode,
            @RequestParam("deleteClientCode") String deleteClientCode);

    @GetMapping(value = "${security.feign.authenticateWithOneTimeToken:/api/security/authenticateWithOneTimeToken/{pathToken}}")
    ContextAuthentication authenticateWithOneTimeToken(
            @PathVariable("pathToken") String pathToken,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader(name = "clientCode", required = false) String clientCode,
            @RequestHeader(name = "appCode", required = false) String headerAppCode,
            @RequestHeader("X-Real-IP") String ipAddress);

    @GetMapping(value = "${security.feign.getUserInternal:/api/security/users/internal/{id}}")
    User getUserInternal(@PathVariable("id") BigInteger id,
            @RequestParam MultiValueMap<String, String> params);

    @GetMapping(value = "${security.feign.getUserInternal:/api/security/users/internal}")
    List<User> getUserInternal(
            @RequestParam List<BigInteger> userIds, @RequestParam MultiValueMap<String, String> params);

    @GetMapping(value = "${security.feign.getClientInternal:/api/security/clients/internal/{id}}")
    Map<String, Object> getClientInternal(
            @PathVariable("id") BigInteger id, @RequestParam MultiValueMap<String, String> params);

    @GetMapping(value = "${security.feign.getClientInternal:/api/security/clients/internal}")
    List<Map<String, Object>> getClientInternal(
            @RequestParam List<BigInteger> clientIds, @RequestParam MultiValueMap<String, String> params);

    @GetMapping(value = "${security.feign.getProfileUsers:/api/security/app/profiles/internal/users}")
    List<BigInteger> getProfileUsers(
            @RequestHeader("appCode") String headerAppCode, @RequestParam List<BigInteger> profileIds);

    @GetMapping(value = "${security.feign.getUserSubOrgInternal:/api/security/users/internal/{userId}/sub-org}")
    List<BigInteger> getUserSubOrgInternal(
            @PathVariable BigInteger userId, @RequestParam String appCode,
            @RequestParam BigInteger clientId);

    @GetMapping(value = "${security.feign.getUserAdminEmails:/api/security/users/internal/adminEmails}")
    Map<String, Object> getUserAdminEmailsInternal(
            @RequestHeader(name = "clientCode") String clientCode,
            @RequestHeader(name = "appCode") String headerAppCode);

    @PostMapping(value = "${security.feign.readClientPageFilterInternal:/api/security/clients/internal/query}")
    Page<Client> readClientPageFilterInternal(
            @RequestBody Query query, @RequestParam MultiValueMap<String, String> queryParams);

    @PostMapping(value = "${security.feign.readUserPageFilterInternal:/api/security/users/internal/query}")
    Page<User> readUserPageFilterInternal(
            @RequestBody Query query, @RequestParam MultiValueMap<String, String> queryParams);

    @PostMapping(value = "${security.feign.getUsersForNotification:/api/security/users/internal/notification}")
    List<NotificationUser> getUsersForNotification(@RequestBody UsersListRequest request);

	@PostMapping(value = "${security.feign.getUsersForEntityProcessor:/api/security/users/internal/processor}")
	List<EntityProcessorUser> getUsersForEntityProcessor(@RequestBody UsersListRequest request);
}
