package com.fincity.saas.commons.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class AbstractBaseConfiguration implements WebFluxConfigurer {

	@Autowired
	protected ObjectMapper objectMapper;

	protected void initialize() {
		this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.ALWAYS));
		this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_EMPTY, Include.ALWAYS));
	}

	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

		configurer.defaultCodecs()
		        .jackson2JsonDecoder(new Jackson2JsonDecoder(this.objectMapper));
		configurer.defaultCodecs()
		        .jackson2JsonEncoder(new Jackson2JsonEncoder(this.objectMapper));
		WebFluxConfigurer.super.configureHttpMessageCodecs(configurer);
	}

	@Override
	public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
		configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 31);
	}
}
