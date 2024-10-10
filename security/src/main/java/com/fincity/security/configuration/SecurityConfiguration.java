package com.fincity.security.configuration;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.SecurityMessageResourceService;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfiguration extends AbstractJooqBaseConfiguration
		implements ISecurityConfiguration, IMQConfiguration {

	@Autowired
	protected SecurityMessageResourceService messageResourceService;

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize(messageResourceService);
		Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
		FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

			if (name != null)
				log.debug("{} - {}", name, v);
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

				"/api/security/verifyToken",

				"/api/security/clients/internal/**",

				"/api/security/applications/internal/**",

				"/api/security/clienturls/internal/**",

				"/api/security/internal/securityContextAuthentication",

				"/api/security/users/findUserClients",

				"/api/security/clients/register",

				"/api/security/clients/socialRegister",

				"/api/security/clients/socialRegister/callback",

				"/api/security/clients/socialRegister/evoke",

				"/api/security/clients/generateCode",

				"/api/security/users/requestResetPassword",

				"/api/security/applications/applyAppCodeSuffix",

				"/api/security/ssl/token/**",

				"/api/security/applications/dependencies",

				"/api/security/applications/internal/dependencies",

				"/api/security/clients/register/events");
	}

}
