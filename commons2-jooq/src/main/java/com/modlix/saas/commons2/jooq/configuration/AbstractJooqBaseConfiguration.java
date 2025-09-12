package com.modlix.saas.commons2.jooq.configuration;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.configuration.AbstractBaseConfiguration;
import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import com.modlix.saas.commons2.jooq.gson.UNumberAdapter;
import com.modlix.saas.commons2.jooq.gson.UNumberListAdapter;
import com.modlix.saas.commons2.jooq.jackson.JSONSerializationModule;
import com.modlix.saas.commons2.jooq.jackson.UnsignedNumbersSerializationModule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.sql.DataSource;
import lombok.Getter;

@Getter
public abstract class AbstractJooqBaseConfiguration extends AbstractBaseConfiguration {

    @Value("${spring.datasource.url}")
    protected String url;

    @Value("${spring.datasource.username}")
    protected String username;

    @Value("${spring.datasource.password}")
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
                .registerTypeAdapter(new TypeToken<List<ULong>>() {
                }.getType(), new UNumberListAdapter<>(ULong.class))
                .registerTypeAdapter(UInteger.class, new UNumberAdapter<>(UInteger.class))
                .registerTypeAdapter(
                        new TypeToken<List<UInteger>>() {
                        }.getType(), new UNumberListAdapter<>(UInteger.class))
                .registerTypeAdapter(UShort.class, new UNumberAdapter<>(UShort.class))
                .registerTypeAdapter(new TypeToken<List<UShort>>() {
                }.getType(), new UNumberListAdapter<>(UShort.class))
                .create();
    }

    @Bean
    DSLContext context(DataSource dataSource) {
        return DSL.using(dataSource, org.jooq.SQLDialect.MYSQL);
    }
}
