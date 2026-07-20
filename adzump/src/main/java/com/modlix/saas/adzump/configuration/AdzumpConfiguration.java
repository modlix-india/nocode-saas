package com.modlix.saas.adzump.configuration;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.jooq.configuration.AbstractJooqBaseConfiguration;
import com.modlix.saas.commons2.security.ISecurityConfiguration;
import com.modlix.saas.commons2.security.service.IAuthenticationService;
import com.modlix.saas.commons2.security.util.LogUtil;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;

@Configuration
public class AdzumpConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    private final AdzumpMessageResourceService messageService;

    public AdzumpConfiguration(AdzumpMessageResourceService messageService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.messageService = messageService;
    }

    @PostConstruct
    @Override
    public void initialize() {
        super.initialize(messageService);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, IAuthenticationService authService, ObjectMapper om)
            throws Exception {
        return this.springSecurityFilterChain(http, authService, om, "/api/adzump/internal/**");
    }

    @Bean
    RequestInterceptor feignInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // Get debug code from MDC and add it to the request headers
                String debugCode = MDC.get(LogUtil.DEBUG_KEY);
                if (debugCode != null) {
                    template.header(LogUtil.DEBUG_KEY, debugCode);
                }
            }
        };
    }
}
