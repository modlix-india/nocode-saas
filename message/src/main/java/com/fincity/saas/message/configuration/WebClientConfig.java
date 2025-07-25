package com.fincity.saas.message.configuration;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationInterceptor;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationScheme;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.MessageConnectionService;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private static final String DEFAULT_WHATSAPP_BASE_URL = "https://graph.facebook.com/";
    private static final String DEFAULT_EXOTEL_SUBDOMAIN = "api.exotel.com";
    private final MessageConnectionService messageConnectionService;

    public Mono<WebClient> createWhatsappWebClient(String appCode, String clientCode) {
        return this.getConnectionDetails(appCode, "whatsapp", clientCode, ConnectionSubType.MESSAGE_WHATSAPP)
                .flatMap(this::createWhatsappWebClient);
    }

    public Mono<WebClient> createWhatsappWebClient(Connection connection) {
        String token = (String) connection.getConnectionDetails().getOrDefault("token", "");
        String baseUrl = (String) connection.getConnectionDetails().getOrDefault("baseUrl", DEFAULT_WHATSAPP_BASE_URL);

        return Mono.just(WebClient.builder()
                .baseUrl(baseUrl)
                .filter(new ReactiveAuthenticationInterceptor(token, ReactiveAuthenticationScheme.BEARER))
                .build());
    }

    public Mono<WebClient> createExotelWebClient(String appCode, String clientCode) {
        return this.getConnectionDetails(appCode, "exotel", clientCode, ConnectionSubType.CALL_EXOTEL)
                .flatMap(this::createExotelWebClient);
    }

    public Mono<WebClient> createExotelWebClient(Connection connection) {
        Map<String, Object> details = connection.getConnectionDetails();
        String apiKey = (String) details.getOrDefault("apiKey", "");
        String apiToken = (String) details.getOrDefault("apiToken", "");
        String subdomain = (String) details.getOrDefault("subdomain", DEFAULT_EXOTEL_SUBDOMAIN);

        String schema = "https";
        String userInfo = apiKey + ":" + apiToken;

        try {
            URI baseUri = new URI(schema, userInfo, subdomain, -1, "", null, null);
            return Mono.just(WebClient.builder()
                    .baseUrl(baseUri.toString())
                    .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .build());
        } catch (URISyntaxException exception) {
            return Mono.error(new IllegalArgumentException("Invalid Exotel URI components: " + exception.getMessage()));
        }
    }

    public WebClient createBasicAuthWebClient(Connection connection) {
        String username = (String) connection.getConnectionDetails().getOrDefault("username", "");
        String password = (String) connection.getConnectionDetails().getOrDefault("password", "");
        String baseUrl = (String) connection.getConnectionDetails().getOrDefault("baseUrl", "");

        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter(new ReactiveAuthenticationInterceptor(token, ReactiveAuthenticationScheme.BASIC))
                .build();
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

    private Mono<Connection> getConnectionDetails(
            String appCode, String connectionName, String clientCode, ConnectionSubType expectedSubType) {
        return FlatMapUtil.flatMapMono(
                        () -> messageConnectionService.getConnection(appCode, connectionName, clientCode),
                        connection -> {
                            if (expectedSubType != null && connection.getConnectionSubType() != expectedSubType)
                                return Mono.error(new IllegalArgumentException("Connection " + connectionName
                                        + " is not of expected type " + expectedSubType));
                            return Mono.just(connection);
                        })
                .contextWrite(Context.of("method", "WebClientConfig.getConnectionDetails"));
    }

    @Bean
    public WebClient.Builder whatsappWebClientBuilder() {
        return WebClient.builder().baseUrl(DEFAULT_WHATSAPP_BASE_URL);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
