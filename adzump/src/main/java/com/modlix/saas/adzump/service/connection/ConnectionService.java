package com.modlix.saas.adzump.service.connection;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.feign.IFeignCoreService;
import com.modlix.saas.adzump.model.connection.ConnectionSummary;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

import feign.FeignException;

/**
 * J2 - connections. Thin wrapper over the Core connection service: resolves a
 * usable Meta/Google credential for the logged-in client. Adzump stores no
 * tokens; Core owns the connections and their refresh.
 *
 * Reads carry NO @PreAuthorize (any authenticated caller). The client scope is
 * ALWAYS taken from the caller's ContextAuthentication, never from a request
 * body, so the Core lookup can never cross tenants.
 */
@Service
public class ConnectionService {

    private static final String GOOGLE_CONNECTION_NAME = "GOOGLE_API";
    private static final String META_CONNECTION_NAME = "META_API";

    private final IFeignCoreService feignCoreService;
    private final AdzumpMessageResourceService msgService;

    public ConnectionService(IFeignCoreService feignCoreService, AdzumpMessageResourceService msgService) {
        this.feignCoreService = feignCoreService;
        this.msgService = msgService;
    }

    /**
     * Resolves a usable platform credential for the logged-in client via the Core
     * internal OAuth2 token endpoint (the same GOOGLE_API / META_API connections
     * the adzump Python services use today).
     */
    public PlatformCredential resolve(Platform platform) {

        if (platform == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "platform");

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        String connectionName = platform == Platform.GOOGLE ? GOOGLE_CONNECTION_NAME : META_CONNECTION_NAME;

        String accessToken;
        try {
            accessToken = this.feignCoreService.getConnectionOAuth2Token(
                    ca.getLoggedInFromClientCode(), ca.getUrlAppCode(), connectionName);
        } catch (FeignException fe) {
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg, fe),
                    AdzumpMessageResourceService.CONNECTION_NOT_FOUND, connectionName);
        }

        if (accessToken == null || accessToken.isBlank())
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.CONNECTION_NOT_FOUND, connectionName);

        // TODO(P1): resolve the account context too (accountId + pageId/pixelId/mcc
        // attributes + expiry) from the Core connection record, and add the
        // short-TTL token cache (platform cache prefix resolves to 'cmn';
        // key by client + platform + account).
        return new PlatformCredential()
                .setPlatform(platform)
                .setAccessToken(accessToken)
                .setAttributes(Map.of());
    }

    /**
     * Backs GET /api/adzump/connections.
     */
    public List<ConnectionSummary> listConnections() {

        // P0 static stub - real listing (per-client Core connection read with
        // connected/account counts) lands P1 (J2 §5.4).
        // TODO(P1): read the client's Core connection records and report the real
        // connected state + account counts.
        return List.of(
                new ConnectionSummary()
                        .setPlatform(Platform.META)
                        .setConnectionName(META_CONNECTION_NAME)
                        .setConnected(false)
                        .setAccountCount(0),
                new ConnectionSummary()
                        .setPlatform(Platform.GOOGLE)
                        .setConnectionName(GOOGLE_CONNECTION_NAME)
                        .setConnected(false)
                        .setAccountCount(0));
    }
}
