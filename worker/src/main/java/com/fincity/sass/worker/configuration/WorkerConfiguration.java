package com.fincity.sass.worker.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.sass.worker.service.WorkerMessageResourceService;
import com.modlix.saas.commons2.jooq.configuration.AbstractJooqBaseConfiguration;
import com.modlix.saas.commons2.security.ISecurityConfiguration;
import com.modlix.saas.commons2.security.service.IAuthenticationService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WorkerConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    private final WorkerMessageResourceService messageService;

    @Autowired
    protected WorkerConfiguration(WorkerMessageResourceService messageService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.messageService = messageService;
    }

    @PostConstruct
    @Override
    public void initialize() {
        super.initialize(messageService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, IAuthenticationService authService) throws Exception {
        return this.springSecurityFilterChain(
                http, authService, this.objectMapper, "/api/worker/client-schedule-controls/monitor");
    }
}
