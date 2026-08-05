package com.fincity.saas.message.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.service.MessageResourceService;

import jakarta.annotation.PostConstruct;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

@Configuration
public class MessageConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    protected MessageResourceService messageResourceService;

    protected MessageConfiguration(MessageResourceService messageResourceService, ObjectMapper objectMapper) {
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
                signal.getContextView()
                        .getOrEmpty(LogUtil.DEBUG_KEY)
                        .ifPresent(dc -> log.debug("{} - {}", name,
                                !dc.toString().startsWith("full-") && v.length() > 500 ? v.substring(0, 500) + "..."
                                        : v));
            else
                log.debug(v);
        }));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(
                http,
                authService,
                this.objectMapper,
                "/api/message/call/callback",
                "/api/message/call/callback/**",
                "/api/message/call/exotel/connect",
                "/api/message/webhooks",
                "/api/message/webhooks/**",
                // Service-to-service reads and sends that entity-processor fronts. Listed
                // explicitly because the generic "(.*internal.*)" entry in ISecurityConfiguration
                // goes to pathMatchers, which takes a PathPattern rather than a regex and so
                // matches nothing. nginx is what actually keeps these off the public internet.
                "/api/message/whatsapp/internal/**",
                // Bare and wildcard forms both listed, matching how the products entry is written
                // in ProcessorConfiguration: these two controllers expose an exact "/internal"
                // route as well as sub-paths, and relying on "/internal/**" to also cover the bare
                // segment is the kind of assumption that fails silently as a 401.
                "/api/message/whatsapp/templates/internal",
                "/api/message/whatsapp/templates/internal/**",
                "/api/message/whatsapp/phone-numbers/internal",
                "/api/message/whatsapp/phone-numbers/internal/**",
                "/api/message/whatsapp/ticket/internal/**",
                "/api/message/call/exotel/internal/**");
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
