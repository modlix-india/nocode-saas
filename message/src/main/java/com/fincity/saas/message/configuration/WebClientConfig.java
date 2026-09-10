package com.fincity.saas.message.configuration;

import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.configuration.call.exotel.ExotelIntegrationsApiConfig;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationInterceptor;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationScheme;
import com.fincity.saas.message.oserver.core.document.Connection;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient builders for the outbound providers this service talks to.
 *
 * <p>The WhatsApp builder went with the Cloud API. Nothing here reaches Meta any more: WhatsApp
 * leaves this service through the bridge client, over HMAC-signed HTTP to a private address, and the
 * bridge holds the WhatsApp connection itself.
 */
// TODO: Move to new WebClient in new spring boot 4.0
@Component
public class WebClientConfig {

    public Mono<WebClient> createExotelWebClient(Connection connection) {
        Map<String, Object> details = connection.getConnectionDetails();
        String apiKey = (String) details.getOrDefault("apiKey", "");
        String apiToken = (String) details.getOrDefault("apiToken", "");
        String accountSid = (String) details.getOrDefault("accountSid", "");
        // Defaults to the global host, which is the host this was hardcoded to before the
        // subdomain became configurable. A regional account needs its own — an India account
        // answers 401 on api.exotel.com and only works on api.in.exotel.com — but that belongs
        // on the connection, not in the default. Defaulting to a region would silently move
        // every tenant that has not set this onto a host their account may not live on, which
        // presents as an authentication failure with nothing in the connection to explain it.
        String subdomain = (String) details.getOrDefault("subdomain", ExotelApiConfig.DEFAULT_API_SUBDOMAIN);
        if (subdomain == null || subdomain.isBlank()) subdomain = ExotelApiConfig.DEFAULT_API_SUBDOMAIN;

        String baseUrl = "https://" + subdomain + "/v1/Accounts/" + accountSid;

        return createBasicAuthWebClient(apiKey, apiToken, baseUrl);
    }

    /**
     * Client for Exotel's Integrations Core API, which browser calling runs on.
     *
     * <p>Every authenticated endpoint on this host takes the token <b>raw</b>, with no scheme prefix
     * — so every call site passes {@link ReactiveAuthenticationScheme#NONE}, which emits the token
     * unchanged. That includes {@code /app}, {@code /app_setting}, {@code /usermapping} and
     * {@code /call/outbound_call}. {@code /token} itself sends no {@code Authorization} header at
     * all, since it is what issues them.
     *
     * <p><b>Do not "fix" these to {@code BEARER}.</b> Prefixing the token is not merely
     * unnecessary, it fails: Exotel answers {@code HTTP 500} with
     * {@code {"error":"invalid AuthToken: malformed"}}, which reads as a provider outage rather than
     * a client mistake. This comment previously claimed the opposite for three of those endpoints,
     * and acting on it would break app creation, agent provisioning, app settings and token minting
     * at once.
     *
     * <p>The scheme stays a parameter rather than becoming a constant so the choice is visible at
     * each call site, and so a provider that does use a prefix can be added without special-casing
     * this method.
     */
    public Mono<WebClient> createExotelIntegrationsWebClient(
            Connection connection, String token, ReactiveAuthenticationScheme scheme) {

        WebClient.Builder builder = WebClient.builder().baseUrl(integrationsBaseUrl(connection));

        if (token != null) builder = builder.filter(new ReactiveAuthenticationInterceptor(token, scheme));

        return Mono.just(builder.build());
    }

    /** Unauthenticated client, for the token endpoint itself. */
    public Mono<WebClient> createExotelIntegrationsWebClient(Connection connection) {
        return this.createExotelIntegrationsWebClient(connection, null, ReactiveAuthenticationScheme.NONE);
    }

    private String integrationsBaseUrl(Connection connection) {
        return (String) connection
                .getConnectionDetails()
                .getOrDefault(
                        ExotelIntegrationsApiConfig.INTEGRATIONS_BASE_URL,
                        ExotelIntegrationsApiConfig.DEFAULT_INTEGRATIONS_BASE);
    }

    public Mono<WebClient> createBasicAuthWebClient(String username, String password, String baseUrl) {
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

        return Mono.just(WebClient.builder()
                .baseUrl(baseUrl)
                .filter(new ReactiveAuthenticationInterceptor(token, ReactiveAuthenticationScheme.BASIC))
                .build());
    }

    public Mono<WebClient> createBasicAuthWebClient(Connection connection) {
        String username = (String) connection.getConnectionDetails().getOrDefault("username", "");
        String password = (String) connection.getConnectionDetails().getOrDefault("password", "");
        String baseUrl = (String) connection.getConnectionDetails().getOrDefault("baseUrl", "");

        return createBasicAuthWebClient(username, password, baseUrl);
    }

    public WebClient createApiKeyWebClient(Connection connection) {
        String apiKey = (String) connection.getConnectionDetails().getOrDefault("apiKey", "");
        String baseUrl = (String) connection.getConnectionDetails().getOrDefault("baseUrl", "");
        String headerName = (String) connection.getConnectionDetails().getOrDefault("headerName", "X-API-Key");

        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(new ReactiveAuthenticationInterceptor(apiKey, ReactiveAuthenticationScheme.NONE, headerName))
                .build();
    }
}
