package com.fincity.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import com.fincity.saas.commons.util.LogUtil;

import jakarta.annotation.PostConstruct;

@EnableAutoConfiguration
@Configuration
public class GatewayConfiguration extends AbstractBaseConfiguration {

	@PostConstruct
	@Override
	protected void initialize() {
		super.initialize();
		Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
		FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

			if (name != null)
				log.debug("{} - {}", name, v);
			else
				log.debug(v);
		}));
	}

	@Bean
	RouteLocator applicationRoutes(RouteLocatorBuilder builder) {
		return builder.routes()
				.build();
	}
}
