package com.modlix.saas.notification.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.jooq.configuration.AbstractJooqBaseConfiguration;
import com.modlix.saas.commons2.security.ISecurityConfiguration;
import com.modlix.saas.commons2.security.service.IAuthenticationService;
import com.modlix.saas.commons2.security.util.LogUtil;
import com.modlix.saas.notification.service.NotificationMessageResourceService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import javax.annotation.PostConstruct;

@Configuration
public class NotificationConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    private final NotificationMessageResourceService messageService;

    protected NotificationConfiguration(NotificationMessageResourceService messageService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.messageService = messageService;
    }

    @PostConstruct
    @Override
    public void initialize() {

        super.initialize(messageService);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, IAuthenticationService authService, ObjectMapper om) throws Exception {
        return this.springSecurityFilterChain(http, authService, om);
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
