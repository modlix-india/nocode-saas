package com.fincity.saas.message.configuration;

import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.configuration.call.exotel.ExotelIntegrationsApiConfig;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
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
        // Defaults to the global host. A regional account sets its own (an India account answers 401 on
        // api.exotel.com); defaulting to a region would move every unset tenant onto a host it may not live on.
        String subdomain = (String) details.getOrDefault("subdomain", ExotelApiConfig.DEFAULT_API_SUBDOMAIN);
        if (subdomain == null || subdomain.isBlank()) subdomain = ExotelApiConfig.DEFAULT_API_SUBDOMAIN;

        String baseUrl = "https://" + subdomain + "/v1/Accounts/" + accountSid;

        return createBasicAuthWebClient(apiKey, apiToken, baseUrl);
    }

    /**
     * Client for Exotel's recording host, with the v1 API's Basic credentials. The caller checks the absolute URL
     * with {@code ExotelApiConfig.isRecordingUrl}; redirects are not followed, so credentials reach no other host.
     */
    public Mono<WebClient> createExotelRecordingWebClient(Connection connection) {
        Map<String, Object> details = connection.getConnectionDetails();
        String apiKey = (String) details.getOrDefault("apiKey", "");
        String apiToken = (String) details.getOrDefault("apiToken", "");
        String token = Base64.getEncoder().encodeToString((apiKey + ":" + apiToken).getBytes());

        return Mono.just(WebClient.builder()
                .filter(new ReactiveAuthenticationInterceptor(token, ReactiveAuthenticationScheme.BASIC))
                .build());
    }

    /**
     * Client for Exotel's Integrations Core API, which browser calling runs on. Every authenticated endpoint here
     * takes the token raw, so call sites pass {@link ReactiveAuthenticationScheme#NONE}.
     *
     * <p>Do not change these to {@code BEARER}: Exotel answers {@code HTTP 500}
     * {@code {"error":"invalid AuthToken: malformed"}}, which reads as a provider outage.
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

    /** Client for TeleCMI's REST API. No auth filter: TeleCMI takes the app id and secret in each JSON body. */
    public Mono<WebClient> createTelecmiWebClient(Connection connection) {
        Object base = connection.getConnectionDetails().get(TelecmiApiConfig.REST_BASE_URL);

        return Mono.just(WebClient.builder()
                .baseUrl(base instanceof String url && !url.isBlank() ? url : TelecmiApiConfig.DEFAULT_REST_BASE)
                .build());
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
