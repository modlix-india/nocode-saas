package com.fincity.saas.ui.model.billing;

/**
 * The app/site hosting-gate decision returned by security. When {@code suspended}
 * is true, {@code serveAppCode}/{@code serveClientCode} point at the configured
 * suspend app/client to render instead of the requested app/site. Mirror of
 * security's record (cross-service services cannot import security's types).
 */
public record HostingDecision(boolean suspended, String serveAppCode, String serveClientCode) {
}
