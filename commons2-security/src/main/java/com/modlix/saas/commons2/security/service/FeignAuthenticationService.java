package com.modlix.saas.commons2.security.service;

import java.math.BigInteger;
import java.util.List;

import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.security.dto.App;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.commons2.security.util.LogUtil;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class FeignAuthenticationService implements IAuthenticationService {

    private static final String CACHE_NAME_BEING_MANAGED = "beingManaged";
    private static final String CACHE_NAME_USER_BEING_MANAGED = "userBeingManaged";
    private static final String CACHE_NAME_APP_READ_ACCESS = "appReadAccess";
    private static final String CACHE_NAME_APP_WRITE_ACCESS = "appWriteAccess";
    private static final String CACHE_NAME_APP_BY_APPCODE_EXPLICIT = "byAppCodeExplicit";
    private static final String CACHE_NAME_APP_DEP_LIST = "appDepList";

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

    public Boolean isBeingManaged(String managingClientCode, String clientCode) {

        return cacheService.cacheEmptyValueOrGet(CACHE_NAME_BEING_MANAGED,
                () -> this.feignAuthService.isBeingManaged(managingClientCode, clientCode), managingClientCode, ":",
                clientCode);
    }

    public Boolean isUserBeingManaged(Object userId, String clientCode) {

        return cacheService.cacheValueOrGet(CACHE_NAME_USER_BEING_MANAGED, () -> {

            BigInteger biUserId = userId instanceof BigInteger id ? id : new BigInteger(userId.toString());

            return this.feignAuthService.isUserBeingManaged(biUserId, clientCode);
        }, clientCode, ":", userId);
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
}
