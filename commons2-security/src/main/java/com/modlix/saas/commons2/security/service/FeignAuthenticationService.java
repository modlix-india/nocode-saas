package com.modlix.saas.commons2.security.service;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.security.dto.App;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.service.CacheService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

    private static final String CACHE_NAME_BEING_MANAGED = "beingManaged";
    private static final String CACHE_NAME_USER_BEING_MANAGED = "userBeingManaged";
    private static final String CACHE_NAME_APP_READ_ACCESS = "appReadAccess";
    private static final String CACHE_NAME_APP_WRITE_ACCESS = "appWriteAccess";
    private static final String CACHE_NAME_APP_BY_APPCODE_EXPLICIT = "byAppCodeExplicit";
    private static final String CACHE_NAME_APP_DEP_LIST = "appDepList";
    private static final String CACHE_NAME_CLIENT_ID_BY_CODE = "clientByCode";

    @Autowired(required = false)
    private IFeignSecurityService feignAuthService;

    @Autowired
    private CacheService cacheService;

    @Override
    public Authentication getAuthentication(boolean isBasic, String bearerToken, String clientCode,
            String appCode, HttpServletRequest request) {

        // Check cache first
        Authentication auth = cacheService.get(CACHE_NAME_TOKEN, bearerToken);

        if (auth instanceof ContextAuthentication ca) {
            if (ca.getUser().getGrantedAuthorities() != null || ca.getUser().getStringAuthorities() != null)
                return auth;
        }

        return this.getAuthenticationFromSecurity(isBasic, bearerToken, clientCode, appCode, request);
    }

    private Authentication getAuthenticationFromSecurity(boolean isBasic, String bearerToken, String clientCode,
            String appCode, HttpServletRequest request) {

        if (feignAuthService == null)
            return null;

        String host = request.getServerName();
        String port = String.valueOf(request.getServerPort());

        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            host = forwardedHost;
        }

        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isEmpty()) {
            port = forwardedPort;
            int ind = port.indexOf(',');
            if (ind != -1) {
                port = port.substring(0, ind);
            }
        }

        return this.feignAuthService
                .contextAuthentication(isBasic ? "basic " + bearerToken : bearerToken, host, port, clientCode, appCode);
    }

    public Boolean isUserClientManageClient(String appCode, BigInteger userId, BigInteger userClientId,
            BigInteger targetClientId) {
        return this.feignAuthService.isUserClientManageClient(appCode, userId, userClientId, targetClientId);
    }

    public Boolean doesClientManageClient(BigInteger managingClientId, BigInteger clientId) {
        return this.feignAuthService.doesClientManageClient(managingClientId, clientId);
    }

    public Boolean doesClientManageClientCode(String managingClientCode, String clientCode) {
        return this.feignAuthService.doesClientManageClientCode(managingClientCode, clientCode);
    }

    public Boolean hasReadAccess(String appCode, String clientCode) {

        return cacheService.cacheValueOrGet(CACHE_NAME_APP_READ_ACCESS,
                () -> this.feignAuthService.hasReadAccess(appCode, clientCode), appCode, ":", clientCode);
    }

    public Boolean hasWriteAccess(String appCode, String clientCode) {

        return cacheService.cacheValueOrGet(CACHE_NAME_APP_WRITE_ACCESS,
                () -> this.feignAuthService.hasWriteAccess(appCode, clientCode), appCode, ":", clientCode);
    }

    public Boolean isValidClientCode(String clientCode) {

        return this.feignAuthService.validClientCode(clientCode);
    }

    public App getAppExplicitInfoByCode(String appCode) {
        return cacheService.cacheValueOrGet(CACHE_NAME_APP_BY_APPCODE_EXPLICIT,
                () -> this.feignAuthService.getAppExplicitInfoByCode(appCode), appCode);
    }

    public List<String> getDependencies(String appCode) {
        return cacheService.cacheValueOrGet(CACHE_NAME_APP_DEP_LIST,
                () -> this.feignAuthService.getDependencies(appCode),
                appCode);
    }

    public BigInteger getClientIdByCode(String clientCode) {
        return cacheService.cacheValueOrGet(
                CACHE_NAME_CLIENT_ID_BY_CODE,
                () -> this.feignAuthService.getClientByCode(clientCode).getId(), clientCode);
    }
}
