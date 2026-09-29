package com.fincity.saas.commons.jooq.configuration;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.jooq.gson.UNumberAdapter;
import com.fincity.saas.commons.jooq.gson.UNumberListAdapter;
import com.fincity.saas.commons.jooq.jackson.JSONSerializationModule;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import lombok.Getter;

/*
 * @EnableConfigurationProperties is load-bearing, not decoration. R2dbcProperties is normally
 * registered by R2dbcAutoConfiguration, so taking it as a bean parameter appears to work - until
 * a context that does not have that auto-configuration, at which point the whole application
 * fails to start with "required a bean of type R2dbcProperties that could not be found".
 *
 * That is exactly what happened to core's integration tests, whose application-test.yml excludes
 * R2dbcAutoConfiguration, R2dbcDataAutoConfiguration and R2dbcTransactionManagerAutoConfiguration
 * deliberately so the suite needs no database. Declaring it here binds spring.r2dbc.pool.* from
 * the Environment directly and makes this class depend on no auto-configuration at all - which
 * was the point, since relying on one is what put the pool sizing out of reach in the first place.
 */
@Getter
@EnableConfigurationProperties(R2dbcProperties.class)
public abstract class AbstractJooqBaseConfiguration extends AbstractBaseConfiguration {

    @Value("${spring.r2dbc.url}")
    protected String url;

    @Value("${spring.r2dbc.username}")
    protected String username;

    @Value("${spring.r2dbc.password}")
    protected String password;


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
     * The one r2dbc pool every query in this service goes through.
     *
     * <p>Until 2026-09-29 there were THREE pools per instance and the configured one was not the
     * one being used:
     *
     * <ol>
     * <li>{@code DRIVER=pool} makes {@code ConnectionFactories.get} return r2dbc-pool's
     * {@code ConnectionPool}, sized from the URL's query parameters - and the URL carries none,
     * so it took the library default of 10.
     * <li>That pool was then wrapped in ANOTHER {@code ConnectionPool} built from
     * {@code ConnectionPoolConfiguration.builder(factory).build()}, also defaulted to 10. jOOQ
     * acquired from the outer, which acquired from the inner.
     * <li>Neither read {@code spring.r2dbc.pool.*}, so Spring Boot's R2dbcAutoConfiguration -
     * which backs off only on a {@code ConnectionFactory} BEAN, and this class published a
     * {@code DSLContext} - built a third, properly configured pool that nothing ever queried.
     * </ol>
     *
     * <p>Measured on production the day it was found: the pool tagged {@code connectionFactory}
     * (Spring's) had {@code max_allocated=20}, exactly as configured, and peak pending 0, because
     * nothing used it. The pool tagged {@code r2dbc} (this one) had {@code max_allocated=10} -
     * the library default, not the configuration - and queued 60 waiters in a single minute under
     * a 4x traffic burst. The setting that would have prevented that was being applied to an idle
     * pool.
     *
     * <p>Now: one pool, built from {@code spring.r2dbc.pool.*}, published as a
     * {@code ConnectionFactory} bean so the auto-configured one is never created. Publishing it
     * is safe - verified that nothing in these services injects {@code ConnectionFactory},
     * {@code R2dbcEntityTemplate} or a reactive transaction manager, so the bean it would have
     * wired to was dead weight holding idle connections open against MySQL.
     *
     * <p>The return type is {@code ConnectionPool}, not {@code ConnectionFactory}, on purpose:
     * {@code ConnectionPoolMetricsAutoConfiguration} is {@code @ConditionalOnBean(ConnectionPool)}
     * and matches on the declared type, so widening it here silently drops the pool metrics. That
     * auto-configuration is also why the meters are no longer bound by hand - doing both would
     * register the same meters twice. NOTE the metric's name tag changes from {@code r2dbc} to
     * the bean name, {@code connectionFactory}.
     *
     * <p>Sizing lives in configuration, not here. Be careful raising it: every instance of every
     * colour holds its own pool, so the ceiling MySQL sees is maxSize x services x instances.
     */
    @Bean
    ConnectionPool connectionFactory(R2dbcProperties properties) {

        Builder props = ConnectionFactoryOptions.parse(url).mutate();

        // DRIVER stays whatever the URL said (mysql). Forcing it to "pool" here is what created
        // the nested pool above; the pooling belongs to the ConnectionPool we build ourselves.
        ConnectionFactory factory = ConnectionFactories.get(props.option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());

        R2dbcProperties.Pool pool = properties.getPool();

        ConnectionPoolConfiguration.Builder config = ConnectionPoolConfiguration.builder(factory)
                .name("connectionFactory")
                .maxSize(pool.getMaxSize())
                .initialSize(pool.getInitialSize())
                .maxIdleTime(pool.getMaxIdleTime());

        if (pool.getMaxLifeTime() != null) config.maxLifeTime(pool.getMaxLifeTime());
        if (pool.getMaxAcquireTime() != null) config.maxAcquireTime(pool.getMaxAcquireTime());
        if (pool.getMaxCreateConnectionTime() != null)
            config.maxCreateConnectionTime(pool.getMaxCreateConnectionTime());
        if (StringUtils.hasText(pool.getValidationQuery())) config.validationQuery(pool.getValidationQuery());
        else config.validationDepth(pool.getValidationDepth());

        return new ConnectionPool(config.build());
    }

    @Bean
    DSLContext context(ConnectionPool connectionFactory) {
        return DSL.using(connectionFactory);
    }
}
