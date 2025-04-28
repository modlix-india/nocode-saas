package com.fincity.sass.worker.configuration;

import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.sass.worker.service.WorkerMessageResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;

import jakarta.annotation.PostConstruct;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class WorkerConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    private final WorkerMessageResourceService messageService;

    @Autowired
    protected WorkerConfiguration(WorkerMessageResourceService messageService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.messageService = messageService;
    }

    @PostConstruct
    @Override
    public void initialize() {
        super.initialize(messageService);
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
            if (name != null) log.debug("{} - {}", name, v);
            else log.debug(v);
        }));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(
                http,
                authService,
                this.objectMapper,
                "/api/worker/schedulers/monitor");
    }

    @Bean
    public ReactiveHttpRequestInterceptor feignInterceptor() {
        return request -> Mono.deferContextual(ctxView -> {
            if (ctxView.hasKey(LogUtil.DEBUG_KEY)) {
                String key = ctxView.get(LogUtil.DEBUG_KEY);

                request.headers().put(LogUtil.DEBUG_KEY, List.of(key));
            }

            return Mono.just(request);
        });
    }
}
