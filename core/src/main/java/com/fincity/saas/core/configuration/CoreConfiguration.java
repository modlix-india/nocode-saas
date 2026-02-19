package com.fincity.saas.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.configuration.AbstractCoreConfiguration;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.Gson;

import jakarta.annotation.PostConstruct;

@Configuration
public class CoreConfiguration extends AbstractCoreConfiguration {

    protected CoreMessageResourceService messageService;

    protected CoreConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @PostConstruct
    @Override
    public void initialize() {
        super.initialize();
        this.objectMapper.registerModule(new KIRuntimeSerializationModule());
        this.objectMapper.registerModule(new UnsignedNumbersSerializationModule(messageService));
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
            if (name != null)
                signal.getContextView()
                        .getOrEmpty(LogUtil.DEBUG_KEY)
                        .ifPresent(dc -> log.debug("{} - {}", name,
                                !dc.toString().startsWith("full-") && v.length() > 500 ? v.substring(0, 500) + "..."
                                        : v));
            else
                log.debug(v);
        }));
    }

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(
                http,
                authService,
                this.objectMapper,
                "/api/core/function/**",
                "/api/core/functions/repositoryFilter",
                "/api/core/functions/repositoryFind",
                "/api/core/schemas/repositoryFilter",
                "/api/core/schemas/repositoryFind",
                "/api/core/connections/oauth/evoke",
                "/api/core/connections/oauth/callback",
                "/api/core/connections/internal",
                "/api/core/connections/internal/**",
                "/api/core/notifications/internal/**");
    }

    @Bean
    Gson gson() {
        Gson baseGson = super.makeGson();

        // Set Gson on adapters that need it (for circular references)
        ArraySchemaType.ArraySchemaTypeAdapter arraySchemaTypeAdapter = new ArraySchemaType.ArraySchemaTypeAdapter();
        AdditionalType.AdditionalTypeAdapter additionalTypeAdapter = new AdditionalType.AdditionalTypeAdapter();

        baseGson = baseGson.newBuilder()
                .registerTypeAdapter(Type.class, new Type.SchemaTypeAdapter())
                .registerTypeAdapter(AdditionalType.class,
                        additionalTypeAdapter)
                .registerTypeAdapter(ArraySchemaType.class, arraySchemaTypeAdapter)

                .create();

        arraySchemaTypeAdapter.setGson(baseGson);
        additionalTypeAdapter.setGson(baseGson);

        return baseGson;
    }
}
