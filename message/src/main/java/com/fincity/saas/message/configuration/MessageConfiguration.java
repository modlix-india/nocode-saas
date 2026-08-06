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
                // Meta's webhook, the Graph-backed message and template routes and the phone-number
                // sync all went with the Cloud API, and their permit-all entries went with them. A
                // permitAll for a path no controller serves is not harmless: it is a standing
                // invitation for something later to be mounted there and be public by accident.
                //
                // Service-to-service routes are listed explicitly because the generic
                // "(.*internal.*)" entry in ISecurityConfiguration goes to pathMatchers, which takes
                // a PathPattern rather than a regex and so matches nothing. nginx is what actually
                // keeps these off the public internet.
                //
                // Session control that entity-processor fronts for the UI. Both forms listed,
                // because create is a POST to the bare "/internal" and everything else hangs below
                // it; relying on "/internal/**" to also cover the bare segment is the assumption
                // that fails silently as a 401.
                "/api/message/whatsapp/sessions/internal",
                "/api/message/whatsapp/sessions/internal/**",
                "/api/message/call/exotel/internal/**",
                // Bridge control plane. Named one route at a time rather than as
                // "/api/message/bridges/**", because these carry their own credentials (an HMAC over
                // the raw body, plus a bootstrap secret on the two that need it) while the fleet
                // view at GET /api/message/bridges does not, and must keep going through normal
                // authentication: it exposes instance ids and internal base URLs.
                "/api/message/bridges/register",
                "/api/message/bridges/release",
                "/api/message/bridges/*/heartbeat",
                "/api/message/bridges/*/events");
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
