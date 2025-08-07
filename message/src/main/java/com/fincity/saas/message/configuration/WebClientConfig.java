package com.fincity.saas.message.configuration;

import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationInterceptor;
import com.fincity.saas.message.configuration.interceptor.ReactiveAuthenticationScheme;
import com.fincity.saas.message.configuration.message.whatsapp.WhatsappApiConfig;
import com.fincity.saas.message.oserver.core.document.Connection;
import java.util.Base64;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

// TODO: Move to new WebClient Config in new spring boot 4.0
@Configuration
public class WebClientConfig {

    public Mono<WebClient> createWhatsappWebClient(Connection connection) {
        String token = (String) connection.getConnectionDetails().getOrDefault("token", "");
        String baseUrl =
                (String) connection.getConnectionDetails().getOrDefault("baseUrl", WhatsappApiConfig.BASE_DOMAIN);

        return Mono.just(WebClient.builder()
                .baseUrl(baseUrl)
                .filter(new ReactiveAuthenticationInterceptor(token, ReactiveAuthenticationScheme.BEARER))
                .build());
    }

    public Mono<WebClient> createExotelWebClient(Connection connection) {
        Map<String, Object> details = connection.getConnectionDetails();
        String apiKey = (String) details.getOrDefault("apiKey", "");
        String apiToken = (String) details.getOrDefault("apiToken", "");
        String accountSid = (String) details.getOrDefault("accountSid", "");

        String baseUrl = ExotelApiConfig.BASE_DOMAIN + "/" + accountSid;

        return createBasicAuthWebClient(apiKey, apiToken, baseUrl);
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
