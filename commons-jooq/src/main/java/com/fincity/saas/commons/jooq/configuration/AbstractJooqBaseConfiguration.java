package com.fincity.saas.commons.jooq.configuration;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

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

    @Bean
    DSLContext context() {

        Builder props = ConnectionFactoryOptions.parse(url).mutate();
        ConnectionFactory factory = ConnectionFactories.get(props.option(ConnectionFactoryOptions.DRIVER, "pool")
                .option(ConnectionFactoryOptions.PROTOCOL, "mysql")
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build());
        return DSL.using(
                new ConnectionPool(ConnectionPoolConfiguration.builder(factory).build()));
    }
}
