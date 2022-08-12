package com.fincity.security.configuration;

import javax.annotation.PostConstruct;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fincity.security.module.CustomJacksonModule;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.MessageResourceService;

import io.r2dbc.spi.ConnectionFactory;

@Configuration
public class SecurityConfiguration implements WebFluxConfigurer {

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected MessageResourceService messageResourceService;

	@PostConstruct
	public void initialize() {
		this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.ALWAYS));
		this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_EMPTY, Include.ALWAYS));
		this.objectMapper.registerModule(new CustomJacksonModule(messageResourceService));
	}

	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

		configurer.defaultCodecs()
		        .jackson2JsonDecoder(new Jackson2JsonDecoder(this.objectMapper));
		configurer.defaultCodecs()
		        .jackson2JsonEncoder(new Jackson2JsonEncoder(this.objectMapper));
		WebFluxConfigurer.super.configureHttpMessageCodecs(configurer);
	}

	@Bean
	DSLContext context(ConnectionFactory factory) {
		return DSL.using(factory);
	}

	@Bean
	SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, AuthenticationService authService) {
		http.csrf()
		        .disable()
		        .cors()
		        .disable()
		        .authorizeExchange(exchanges -> exchanges.pathMatchers(HttpMethod.OPTIONS, "/**")
		                .permitAll()
		                .pathMatchers("**/internal/**")
		                .permitAll()
		                .pathMatchers("/actuator/**")
		                .permitAll()
		                .pathMatchers("/api/security/authenticate")
		                .permitAll()
		                .pathMatchers("/api/**")
		                .authenticated())
		        .addFilterAt(new JWTTokenFilter(authService), SecurityWebFiltersOrder.HTTP_BASIC)
		        .formLogin()
		        .disable()
		        .logout()
		        .disable();

		return http.build();
	}
	
	@Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
    }

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
