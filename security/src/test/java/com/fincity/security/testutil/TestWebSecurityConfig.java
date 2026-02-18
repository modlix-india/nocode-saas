package com.fincity.security.testutil;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@TestConfiguration
public class TestWebSecurityConfig {

	@Bean
	SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
				.build();
	}
}
