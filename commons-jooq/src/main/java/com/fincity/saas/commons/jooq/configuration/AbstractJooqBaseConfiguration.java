package com.fincity.saas.commons.jooq.configuration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.r2dbc.ConnectionPoolMetrics;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.jooq.gson.UNumberAdapter;
import com.fincity.saas.commons.jooq.gson.UNumberListAdapter;
import com.fincity.saas.commons.jooq.jackson.JSONSerializationModule;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ValidationDepth;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import lombok.Getter;

@Getter
public abstract class AbstractJooqBaseConfiguration extends AbstractBaseConfiguration {

    @Value("${spring.r2dbc.url}")
    protected String url;

    @Value("${spring.r2dbc.username}")
    protected String username;

    @Value("${spring.r2dbc.password}")
    protected String password;

    @Value("${spring.r2dbc.pool.initial-size:5}")
    protected int poolInitialSize;

    @Value("${spring.r2dbc.pool.max-size:10}")
    protected int poolMaxSize;

    @Value("${spring.r2dbc.pool.max-idle-time:30m}")
    protected Duration poolMaxIdleTime;

    @Value("${spring.r2dbc.pool.validation-query:SELECT 1}")
    protected String poolValidationQuery;


    protected AbstractJooqBaseConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    public void initialize(AbstractMessageService messageResourceService) {
        super.initialize();
        this.objectMapper.registerModule(new UnsignedNumbersSerializationModule(messageResourceService));
        this.objectMapper.registerModule(new JSONSerializationModule());
    }

    @Override
    public Gson makeGson() {
        return super.makeGson()
                .newBuilder()
                .registerTypeAdapter(ULong.class, new UNumberAdapter<>(ULong.class))
                .registerTypeAdapter(new TypeToken<List<ULong>>() {}.getType(), new UNumberListAdapter<>(ULong.class))
                .registerTypeAdapter(UInteger.class, new UNumberAdapter<>(UInteger.class))
                .registerTypeAdapter(
                        new TypeToken<List<UInteger>>() {}.getType(), new UNumberListAdapter<>(UInteger.class))
                .registerTypeAdapter(UShort.class, new UNumberAdapter<>(UShort.class))
                .registerTypeAdapter(new TypeToken<List<UShort>>() {}.getType(), new UNumberListAdapter<>(UShort.class))
                .create();
    }

    /**
     * The r2dbc pool every service actually talks to MySQL through, and until now the one thing
     * about those services that nothing measured. Prometheus had HikariCP metrics - which cover
     * only the JDBC path Flyway uses at startup - and nothing at all for this pool, so
     * "connections exhausted" and "the database is slow" looked identical from outside.
     *
     * The metrics are bound by hand rather than by exposing the ConnectionPool as a bean.
     * Spring Boot's ConnectionPoolMetricsAutoConfiguration would pick a bean up automatically,
     * but ConnectionPool implements ConnectionFactory, and R2dbcAutoConfiguration backs off on
     * any ConnectionFactory bean - so publishing one would silently change which pool the rest
     * of the context wires itself to. Binding directly leaves that wiring exactly as it was and
     * adds only the meters.
     *
     * ObjectProvider because the registry is not required for the service to run: no actuator,
     * no meters, and the DSLContext is still built.
     */
    @Bean
    DSLContext context(ObjectProvider<MeterRegistry> meterRegistry) {

        ConnectionPool pool = pool(
                driverFactory(url, username, password),
                poolInitialSize,
                poolMaxSize,
                poolMaxIdleTime,
                poolValidationQuery);

        meterRegistry.ifAvailable(registry ->
                new ConnectionPoolMetrics(pool, "r2dbc", Tags.empty()).bindTo(registry));

        return DSL.using(recovering(pool));
    }

    /**
     * The DRIVER-level factory, deliberately NOT a pool.
     *
     * This used to force {@code DRIVER=pool} and {@code PROTOCOL=mysql}, which makes
     * ConnectionFactories hand back an r2dbc-pool ConnectionPool - and that was then
     * wrapped in a SECOND ConnectionPool below. Two nested pools, both on
     * r2dbc-pool's defaults, each with its own idle connections and its own
     * bookkeeping, and the outer one the only place any configuration could land.
     * One pool, configured once, is the whole point of this method existing.
     */
    static ConnectionFactory driverFactory(String url, String username, String password) {

        Builder props = ConnectionFactoryOptions.parse(url).mutate();

        return ConnectionFactories.get(props.option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
    }

    /**
     * The one pool, built from {@code spring.r2dbc.pool.*}.
     *
     * Those properties were set in every service's yml and read by NOBODY: the pool
     * was built with {@code ConnectionPoolConfiguration.builder(factory).build()} and
     * nothing else, so size, idle time and validation were all r2dbc-pool defaults
     * however the yml was written.
     *
     * The validation half is not cosmetic. Without it a pooled connection whose
     * socket the server has already dropped - after a MySQL restart, a failover or a
     * crash - is handed straight back to the caller, and every query fails with
     * netty's "channel not registered to an event loop" until the SERVICE is
     * restarted. The database recovering is then not enough, which is the wrong
     * failure mode for a pool that outlives the database. Observed across core and
     * security on 2026-10-04.
     */
    static ConnectionPool pool(
            ConnectionFactory factory, int initialSize, int maxSize, Duration maxIdleTime, String validationQuery) {

        ConnectionPoolConfiguration.Builder config = ConnectionPoolConfiguration.builder(factory)
                .initialSize(initialSize)
                .maxSize(maxSize)
                .maxIdleTime(maxIdleTime);

        // A blank query would be sent to the server as a statement, so an operator
        // clearing the property turns validation off rather than breaking every
        // acquire.
        if (validationQuery != null && !validationQuery.isBlank())
            config.validationQuery(validationQuery).validationDepth(ValidationDepth.REMOTE);

        return new ConnectionPool(config.build());
    }

    /** How many stale pooled connections one caller will quietly step over. */
    private static final int STALE_ACQUIRE_RETRIES = 3;

    /**
     * Step over a connection the server has already hung up on.
     *
     * When MySQL dies abruptly, the sockets its pooled connections were using are
     * gone but the pool still holds them. The NEXT acquire gets one and fails with
     * netty's {@code IllegalStateException: channel not registered to an event
     * loop} - thrown from inside validation, so {@code validationQuery} does not
     * catch it and the error reaches the caller as a failed request.
     *
     * The pool does heal itself: a failed acquire discards that connection, so the
     * damage is bounded by how many stale ones it holds, not unbounded. But bounded
     * still means real requests failing for no reason the caller could act on, so
     * they are retried here instead.
     *
     * Deliberately narrow. A genuinely unreachable server surfaces as
     * {@code ConnectException: Connection refused}, NOT IllegalStateException, and
     * must NOT be retried - that would turn a fast, honest failure into a slow one
     * and hide an outage. Measured both on 2026-10-04 against a killed MySQL.
     */
    static ConnectionFactory recovering(ConnectionPool pool) {

        return new ConnectionFactory() {

            @Override
            public Publisher<? extends Connection> create() {
                return Mono.from(pool.create())
                        .retryWhen(Retry.max(STALE_ACQUIRE_RETRIES).filter(AbstractJooqBaseConfiguration::isStaleChannel));
            }

            @Override
            public ConnectionFactoryMetadata getMetadata() {
                return pool.getMetadata();
            }
        };
    }

    /** True only for the dead-socket signal, never for a server that is down. */
    static boolean isStaleChannel(Throwable error) {

        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof IllegalStateException) return true;
            if (t.getCause() == t) break;
        }

        return false;
    }
}
