package com.fincity.security.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.SecurityMessageResourceService;

import jakarta.annotation.PostConstruct;

@Configuration
public class SecurityConfiguration extends AbstractJooqBaseConfiguration
        implements ISecurityConfiguration, IMQConfiguration {

    protected SecurityMessageResourceService messageResourceService;

    public SecurityConfiguration(SecurityMessageResourceService messageResourceService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.messageResourceService = messageResourceService;
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize(messageResourceService);
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

            if (name != null)
                log.debug("{} - {}", name, v.length() > 500 ? v.substring(0, 500) + "..." : v);
            else
                log.debug(v);
        }));
    }

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, AuthenticationService authService) {
        return this.springSecurityFilterChain(http, authService, this.objectMapper,

                "/actuator/**",

                "/api/security/authenticate",

                "/api/security/authenticate/social",

                "api/security/authenticate/otp/generate",

                "/api/security/verifyToken",

                "/api/security/clients/internal/**",

                "/api/security/applications/internal/**",

                "/api/security/clienturls/internal/**",

                "/api/security/internal/securityContextAuthentication",

                "/api/security/users/findUserClients",

                "/api/security/users/reset/password/otp/generate",

                "/api/security/users/reset/password/otp/verify",

                "/api/security/users/reset/password",

                "/api/security/clients/register/otp/generate",

                "/api/security/clients/register/otp/verify",

                "/api/security/clients/register",

                "/api/security/clients/socialRegister",

                "/api/security/clients/socialRegister/callback",

                "/api/security/clients/socialRegister/evoke",

                "/api/security/applications/applyAppCodeSuffix",

                "/api/security/applications/applyAppCodePrefix",

                "/api/security/ssl/token/**",

                "/api/security/applications/dependencies",

                "/api/security/applications/internal/dependencies",

                "/api/security/clients/register/events",

                "api/security/clientOtpPolicy/codes/policy",

                "api/security/clientPasswordPolicy/codes/policy",

                "api/security/clientPinPolicy/codes/policy",

                "/api/security/authenticateWithOneTimeToken/**",

                "/api/security/users/inviteDetails/**",

                "/api/security/users/acceptInvite",

                "/api/security/users/exists",

                "/api/security/users/internal/**",

                "/api/security/users/internal",

                "/api/security/app/profiles/internal",

                "/api/security/app/profiles/internal/**",

                "/api/security/plans/registration",

                "/api/security/plans/internal/limits",

		        "/api/security/otp/generate",

		        "/api/security/otp/verify");
    }

}
