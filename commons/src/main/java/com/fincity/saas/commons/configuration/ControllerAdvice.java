package com.fincity.saas.commons.configuration;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Priority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
@Priority(0)
public class ControllerAdvice implements ErrorWebExceptionHandler {

	private static final ErrorServerContext ERROR_SERVER_CONTEXT = new ErrorServerContext();
	
	@Autowired
	private AbstractMessageService resourceService;
	
	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

		Mono<ServerResponse> sr = null;

		if (ex instanceof GenericException g) {
			sr = ServerResponse.status(g.getStatusCode())
			        .bodyValue(g.toExceptionData());
		}
		if (ex instanceof FeignException fe) {

			Optional<ByteBuffer> byteBuffer = fe.responseBody();
			if (byteBuffer.isPresent() && byteBuffer.get()
			        .hasArray()) {

				Collection<String> ctype = fe.responseHeaders()
				        .get(HttpHeaders.CONTENT_TYPE);
				if (ctype != null && ctype.contains("application/json")) {
					try {
						Map<String, Object> map = this.objectMapper.readValue(byteBuffer.get()
						        .array(), new TypeReference<Map<String, Object>>() {
						        });
						sr = Mono.just(new GenericException(HttpStatus.valueOf(fe.status()),
						        map.get("message") == null ? ""
						                : map.get("message")
						                        .toString(),
						        fe)).flatMap(g -> ServerResponse.status(g.getStatusCode())
						                .bodyValue(g.toExceptionData()));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

		} 
		
		if (sr == null) {

			String eId = GenericException.uniqueId();
			Mono<String> msg = resourceService.getMessage(AbstractMessageService.UNKNOWN_ERROR_WITH_ID, eId);

			log.error("Error : {}", eId, ex);

			final HttpStatus status = (ex instanceof ResponseStatusException rse) ? rse.getStatus()
			        : HttpStatus.INTERNAL_SERVER_ERROR;

			sr = msg.map(m -> new GenericException(status, eId, m, ex))
			        .flatMap(g -> ServerResponse.status(g.getStatusCode())
			                .bodyValue(g.toExceptionData()));
		}

		return sr.flatMap(e -> e.writeTo(exchange, ERROR_SERVER_CONTEXT));
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
