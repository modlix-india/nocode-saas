package com.fincity.saas.core.configuration;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.configuration.AbstractMongoConfiguration;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.service.CoreMessageResourceService;

import feign.Contract;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import jakarta.annotation.PostConstruct;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

@Configuration
public class CoreConfiguration extends AbstractMongoConfiguration
		implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer {

	@Autowired
	private CoreMessageResourceService messageService;

	@Value("${spring.r2dbc.url}")
	private String url;

	@Value("${spring.r2dbc.username}")
	private String username;

	@Value("${spring.r2dbc.password}")
	private String password;

	@PostConstruct
	@Override
	public void initialize() {

		super.initialize();
		this.objectMapper.registerModule(new KIRuntimeSerializationModule());
		this.objectMapper.registerModule(
				new com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule(messageService));
		Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
		FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

			if (name != null)
				log.debug("{} - {}", name, v);
			else
				log.debug(v);
		}));
	}

	@Bean
	DSLContext context() {

		Builder props = ConnectionFactoryOptions.parse(url)
				.mutate();
		ConnectionFactory factory = ConnectionFactories.get(props.option(ConnectionFactoryOptions.DRIVER, "pool")
				.option(ConnectionFactoryOptions.PROTOCOL, "mysql")
				.option(ConnectionFactoryOptions.USER, username)
				.option(ConnectionFactoryOptions.PASSWORD, password)
				.build());
		return DSL.using(new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
				.build()));
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, this.objectMapper, "/api/core/function/**",
				"/api/core/functions/repositoryFilter", "/api/core/functions/repositoryFind");
	}

	@Bean
	ReactiveHttpRequestInterceptor feignInterceptor() {
		return request -> Mono.deferContextual(ctxView -> {

			if (ctxView.hasKey(LogUtil.DEBUG_KEY)) {
				String key = ctxView.get(LogUtil.DEBUG_KEY);

				request.headers()
						.put(LogUtil.DEBUG_KEY, List.of(key));
			}

			return Mono.just(request);
		});
	}

	@Override
	public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
		// This is a method in interface which requires an implementation in a concrete
		// class
		// that is why it is left empty.
	}
}
