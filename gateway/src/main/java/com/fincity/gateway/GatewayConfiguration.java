package com.fincity.gateway;

import javax.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;


@EnableAutoConfiguration
@Configuration
public class GatewayConfiguration extends AbstractBaseConfiguration {
	
	@PostConstruct
	@Override
	protected void initialize() {
		super.initialize();
	}

	@Bean
	RouteLocator applicationRoutes(RouteLocatorBuilder builder) {
		return builder.routes().build();
	}
}
