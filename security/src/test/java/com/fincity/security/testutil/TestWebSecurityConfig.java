package com.fincity.security.testutil;

import java.util.List;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.jackson.SortSerializationModule;
import com.fincity.saas.commons.security.filter.JWTTokenFilter;
import com.fincity.security.service.AuthenticationService;

import reactor.core.publisher.Mono;

@TestConfiguration
public class TestWebSecurityConfig implements WebFluxConfigurer {

	@Bean
	SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http,
			@Autowired(required = false) AuthenticationService authService,
			@Autowired(required = false) ObjectMapper objectMapper) {
		var builder = http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());

		// Add JWT filter only when a real (non-mock) AuthenticationService is available.
		// In @WebFluxTest (controller tests), AuthenticationService is a Mockito mock
		// whose unstubbed getAuthentication() returns null, causing NPEs in the filter.
		// In @SpringBootTest (integration tests), it's the real bean.
		if (authService != null && objectMapper != null
				&& !Mockito.mockingDetails(authService).isMock()) {
			builder.addFilterAt(new JWTTokenFilter(authService, objectMapper),
					SecurityWebFiltersOrder.HTTP_BASIC);
		}

		return builder.build();
	}

	@Bean
	Jackson2ObjectMapperBuilderCustomizer testJacksonCustomizer() {
		return builder -> builder
				.serializationInclusion(JsonInclude.Include.NON_NULL)
				.modules(new CommonsSerializationModule(), new SortSerializationModule());
	}

	@Override
	public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
		configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
	}

	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
		// Use defaults - Spring Boot's auto-configured ObjectMapper with our
		// customizer will be used automatically.
	}

	@Bean
	@Order(-2)
	ErrorWebExceptionHandler testExceptionHandler() {
		return new TestErrorWebExceptionHandler();
	}

	private static class TestErrorWebExceptionHandler implements ErrorWebExceptionHandler {

		private static final ErrorServerContext ERROR_SERVER_CONTEXT = new ErrorServerContext();

		@Override
		public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

			Mono<ServerResponse> sr;

			if (ex instanceof GenericException g) {
				sr = ServerResponse.status(g.getStatusCode())
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(g.toExceptionData());
			} else if (ex instanceof ResponseStatusException rse) {
				sr = ServerResponse.status(rse.getStatusCode())
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(java.util.Map.of(
								"status", rse.getStatusCode().value(),
								"error", rse.getReason() != null ? rse.getReason() : "Error"));
			} else {
				sr = ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(java.util.Map.of(
								"status", 500,
								"error", ex.getMessage() != null ? ex.getMessage() : "Internal Server Error"));
			}

			return sr.flatMap(e -> e.writeTo(exchange, ERROR_SERVER_CONTEXT));
		}
	}

	private static class ErrorServerContext implements ServerResponse.Context {

		private final HandlerStrategies strats = HandlerStrategies.withDefaults();

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return strats.messageWriters();
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return strats.viewResolvers();
		}
	}
}
