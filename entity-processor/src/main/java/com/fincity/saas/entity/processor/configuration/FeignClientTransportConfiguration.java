package com.fincity.saas.entity.processor.configuration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import io.netty.channel.ChannelOption;
import reactivefeign.webclient.WebClientFeignCustomizer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Bounds and instruments every outbound reactive-feign call this service makes.
 *
 * <h2>The incident this comes from</h2>
 *
 * <p>On 2026-10-05 production returned 504s on fifteen unrelated entity-processor endpoints —
 * tickets, partners, campaigns, products, tags — every one at exactly 60.000s, which is nginx's
 * {@code proxy_read_timeout} giving up rather than anything this service decided. They landed on
 * one instance while its sibling was untouched, and that instance was IDLE throughout: 0% process
 * CPU, zero blocked threads, zero pending r2dbc connections, no GC, health UP, and a thread dump
 * byte-for-byte comparable to the healthy one.
 *
 * <p>An idle process that answers nothing is the signature of work parked on IO while holding no
 * thread. In a reactive stack that is invisible to every instrument that usually finds a hang.
 *
 * <h2>Why it could happen, and why nobody could see it</h2>
 *
 * <p><b>Nothing bounded the call.</b> There was no {@code reactive.feign.client} configuration
 * anywhere in this repository, so the clients ran on library defaults and the WebClient default
 * carries no response timeout at all. A dependency that accepts a connection and never answers
 * parks the caller indefinitely; the only limit was nginx's, 60s later, long after the user left.
 *
 * <p><b>The default connection pool can park a caller for 45 seconds on its own.</b> Reactor
 * Netty's defaults are {@code maxConnections = max(cores, 8) * 2}, {@code pendingAcquireMaxCount =
 * 500} and, critically, {@code DEFAULT_POOL_ACQUIRE_TIMEOUT = 45000}. Once the pool is exhausted
 * the next 500 callers queue for up to 45s each, holding no thread and logging nothing, which
 * matches the observed failure exactly. Explicit values here mean the pool is a decision rather
 * than an inherited default nobody chose.
 *
 * <p><b>Nothing measured any of it.</b> entity-processor exported no {@code http_client_requests_*}
 * series whatsoever, so the one hop that could explain the stall was the one hop with no metrics on
 * it. That is why this enables metrics in two places, which is easy to get wrong: {@link
 * HttpClient#metrics} publishes {@code reactor.netty.http.client.*} (request timing, connect time),
 * while the pool's own counters — {@code reactor.netty.connection.provider.pending.connections} and
 * friends — come only from {@link ConnectionProvider.ConnectionPoolSpec#metrics(boolean)}. The
 * pending count is the one that would have settled this incident in a minute, and it is the one the
 * HTTP-level switch does not give you.
 *
 * <h2>Why a customizer rather than YAML</h2>
 *
 * <p>{@code reactive.feign.client.config.*.options} can express timeouts, but it is a map of
 * builder fields resolved reflectively per transport, and a misspelled key there fails silently —
 * the same shape as the bug being fixed. A {@link WebClientFeignCustomizer} bean is a compile-time
 * hook, picked up by {@code ReactiveFeignClientsConfiguration} via
 * {@code @Autowired(required = false)} and applied to every client it builds.
 *
 * <h2>Scope</h2>
 *
 * <p>Registered in entity-processor alone, not in commons, although the feign interfaces are
 * shared. This is a fix for a live incident on one service; giving every service on the platform a
 * new outbound timeout and pool is a separate, reviewed change. The same gap exists everywhere and
 * is worth closing deliberately rather than as a side effect of this.
 */
@Configuration
public class FeignClientTransportConfiguration {

    /**
     * The whole response, not just the connect.
     *
     * <p>A connect timeout would not have helped: the connection established and the peer never
     * answered. Reactor Netty's read timeout is per-read inactivity, which a trickling response
     * defeats indefinitely. {@code responseTimeout} bounds the entire exchange, which is the only
     * bound that describes the observed failure.
     *
     * <p>Ten seconds is measured, not taste: the security service's own worst case for these calls
     * was 1.59s, so this is roughly six times its slowest healthy response — far enough out never
     * to truncate a real answer, close enough that a hung dependency becomes a logged error while
     * the user is still on the page rather than a 60s blank.
     */
    @Value("${entity.processor.feign.responseTimeoutSeconds:10}")
    private long responseTimeoutSeconds;

    /** A dead peer should fail now rather than consume the response budget first. */
    @Value("${entity.processor.feign.connectTimeoutMillis:3000}")
    private int connectTimeoutMillis;

    /**
     * Deliberately larger than the default {@code max(cores, 8) * 2}. These calls are internal,
     * short and fan out per request, so the pool should not be the scarce resource; if saturation
     * ever is the answer, the pending gauge now says so instead of leaving it to be inferred.
     */
    @Value("${entity.processor.feign.maxConnections:100}")
    private int maxConnections;

    /**
     * Five seconds, against a 45-second default.
     *
     * <p>Waiting 45s for a connection cannot help anyone: nginx abandons the request at 60s, so a
     * caller that waits that long produces a failure the user already gave up on. Failing fast
     * turns pool exhaustion into a prompt, logged, countable error.
     */
    @Value("${entity.processor.feign.pendingAcquireTimeoutSeconds:5}")
    private long pendingAcquireTimeoutSeconds;

    @Bean
    WebClientFeignCustomizer feignTransportCustomizer() {

        ConnectionProvider provider = ConnectionProvider.builder("entity-processor-feign")
                .maxConnections(this.maxConnections)
                .pendingAcquireTimeout(Duration.ofSeconds(this.pendingAcquireTimeoutSeconds))
                // Publishes reactor.netty.connection.provider.* — total, active, idle and PENDING
                // connections. The pending gauge is the point of this whole bean.
                .metrics(true)
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(this.responseTimeoutSeconds))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeoutMillis)
                // Publishes reactor.netty.http.client.*. The second argument maps a URI to its
                // metric tag; the identity function would mint a time series per distinct path and,
                // with entity ids in those paths, grow unbounded. These callees are few and
                // internal, so the URI is collapsed and the useful grouping is the client itself.
                .metrics(true, uri -> "feign");

        return builder -> builder.clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
