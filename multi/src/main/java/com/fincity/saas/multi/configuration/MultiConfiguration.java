package com.fincity.saas.multi.configuration;

import java.util.List;

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

import jakarta.annotation.PostConstruct;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class MultiConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    public MultiConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize();
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

            if (name != null)
                log.debug("{} - {}", name, v.length() > 500 ? v.substring(0, 500) + "..." : v);
            else
                log.debug(v);
        }));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(http, authService, this.objectMapper);
    }

    @Bean
    public ReactiveHttpRequestInterceptor feignInterceptor() {
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
