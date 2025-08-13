package com.fincity.saas.message.configuration.interceptor;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class ReactiveAuthenticationInterceptor implements ExchangeFilterFunction {

    private final String token;
    private final ReactiveAuthenticationScheme scheme;
    private final String headerName;

    public ReactiveAuthenticationInterceptor(String token) {
        this(token, ReactiveAuthenticationScheme.BEARER, "Authorization");
    }

    public ReactiveAuthenticationInterceptor(String token, ReactiveAuthenticationScheme scheme) {
        this(token, scheme, "Authorization");
    }

    public ReactiveAuthenticationInterceptor(String token, ReactiveAuthenticationScheme scheme, String headerName) {
        this.token = token;
        this.scheme = scheme;
        this.headerName = headerName;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        ClientRequest newRequest = ClientRequest.from(request)
                .header(headerName, scheme.format(token))
                .build();

        return next.exchange(newRequest);
    }

    public WebClient.Builder addToWebClientBuilder(WebClient.Builder builder) {
        return builder.filter(this);
    }

    public WebClient createWebClient() {
        return WebClient.builder().filter(this).build();
    }
}
