package com.fincity.saas.commons.jooq.configuration;

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
import lombok.Getter;

@Getter
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

        Builder props = ConnectionFactoryOptions.parse(url).mutate();
        ConnectionFactory factory = ConnectionFactories.get(props.option(ConnectionFactoryOptions.DRIVER, "pool")
                .option(ConnectionFactoryOptions.PROTOCOL, "mysql")
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());

        ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory).build());

        meterRegistry.ifAvailable(registry ->
                new ConnectionPoolMetrics(pool, "r2dbc", Tags.empty()).bindTo(registry));

        return DSL.using(pool);
    }
}
