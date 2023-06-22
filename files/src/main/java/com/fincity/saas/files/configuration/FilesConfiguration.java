package com.fincity.saas.files.configuration;

import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;

import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

@Configuration
public class FilesConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

	@Override
	@PostConstruct
	public void initialize() {
		super.initialize();
		Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
		FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

			if (name != null)
				log.debug("{} - {}", name, v);
			else
				log.debug(v.toString());
		}));
	}

	@Bean
	SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
		return this.springSecurityFilterChain(http, authService, this.objectMapper, "/api/files/static/file/**");
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

}
