package com.fincity.gateway;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;

@EnableAutoConfiguration
@Configuration
public class GatewayConfiguration extends AbstractBaseConfiguration {

	@Bean
	RouteLocator applicationRoutes(RouteLocatorBuilder builder) {
		return builder.routes().build();
	}
}
