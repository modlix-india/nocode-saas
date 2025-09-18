package com.fincity.saas.entity.processor.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class ProcessorConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    protected ProcessorMessageResourceService processorMessageResourceService;

    protected ProcessorConfiguration(
            ProcessorMessageResourceService messageResourceService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.processorMessageResourceService = messageResourceService;
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize(processorMessageResourceService);
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
            if (name != null) log.debug("{} - {}", name, v.length() > 500 ? v.substring(0, 500) + "..." : v);
            else log.debug(v);
        }));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(
                http,
                authService,
                this.objectMapper,
                "/api/entity/processor/core/function/**",
                "/api/entity/processor/core/functions/repositoryFilter",
                "/api/entity/processor/core/functions/repositoryFind",
                "/api/entity/processor/tickets/req/campaigns",
                "/api/entity/processor/tickets/req/website/*");
    }
}
