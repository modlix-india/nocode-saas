package com.fincity.saas.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.configuration.AbstractCoreConfiguration;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class CoreConfiguration extends AbstractCoreConfiguration {

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
            if (name != null) log.debug("{} - {}", name, v);
            else log.debug(v);
        }));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(
                http,
                authService,
                this.objectMapper,
                "/api/core/function/**",
                "/api/core/functions/repositoryFilter",
                "/api/core/functions/repositoryFind",
                "/api/core/connections/oauth/evoke",
                "/api/core/connections/oauth/callback",
                "/api/core/connections/internal",
                "/api/core/notifications/internal");
    }
}
