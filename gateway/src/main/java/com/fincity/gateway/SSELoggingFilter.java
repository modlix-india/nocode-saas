package com.fincity.gateway;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Filter for handling Server-Sent Events (SSE) requests.
 * Ensures SSE responses are not buffered by setting appropriate headers.
 */
@Component
public class SSELoggingFilter implements GlobalFilter, Ordered {

    private static final String TEXT_EVENT_STREAM = "text/event-stream";

    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Only apply SSE handling to AI endpoints
        if (path.contains("/api/ai/")) {
            ServerHttpResponse originalResponse = exchange.getResponse();
            ServerHttpResponseDecorator decoratedResponse = new SSEResponseDecorator(originalResponse);
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        }

        return chain.filter(exchange);
    }

    /**
     * Response decorator that ensures proper SSE headers for streaming.
     */
    private static class SSEResponseDecorator extends ServerHttpResponseDecorator {

        public SSEResponseDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            setSSEHeaders();
            return super.writeWith(body);
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            setSSEHeaders();
            return super.writeAndFlushWith(body);
        }

        private void setSSEHeaders() {
            MediaType contentType = getHeaders().getContentType();
            if (contentType != null && contentType.toString().contains(TEXT_EVENT_STREAM)) {
                // Ensure no buffering for SSE
                getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
                getHeaders().set("X-Accel-Buffering", "no");
            }
        }
    }
}
