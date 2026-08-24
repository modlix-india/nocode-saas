package com.fincity.gateway;

import java.util.List;

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

    /**
     * Decides which requests get the unbuffered treatment.
     *
     * <p>Deliberately the request's {@code Accept} header rather than a list of paths. The path list
     * this replaced named {@code /api/ai/} alone, so every stream added afterwards silently missed
     * out: the notification service's in-app stream and the message service's call stream were both
     * already live and neither was covered. Adding a third path would have left the fourth to be
     * discovered the same way.
     *
     * <p>A client asking for {@code text/event-stream} is asking for a stream, whichever service
     * ends up serving it. The decorator is still a no-op unless the response actually comes back as
     * one, so a wrong guess here costs nothing.
     */
    private static boolean wantsEventStream(ServerHttpRequest request) {
        List<MediaType> accepted = request.getHeaders().getAccept();
        return accepted.stream().anyMatch(MediaType.TEXT_EVENT_STREAM::isCompatibleWith);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        if (path.contains("/api/ai/") || wantsEventStream(request)) {
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
