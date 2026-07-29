package com.fincity.security.model.billing;

/**
 * The app/site hosting-gate decision for a tenant page request. When the billed
 * client's builder wallet (appbuilder for apps, sitezump→appbuilder for sites) is
 * suspended, {@code suspended} is true and {@code serveAppCode}/{@code serveClientCode}
 * point at the configured suspend app/client to render instead. Otherwise it is a
 * pass-through of the requested app/client.
 */
public record HostingDecision(boolean suspended, String serveAppCode, String serveClientCode) {

    public static HostingDecision serve(String appCode, String clientCode) {
        return new HostingDecision(false, appCode, clientCode);
    }
}
