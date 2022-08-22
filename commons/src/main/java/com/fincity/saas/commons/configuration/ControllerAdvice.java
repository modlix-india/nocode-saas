package com.fincity.saas.commons.configuration;

import java.util.List;

import javax.annotation.Priority;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
@Priority(0)
public class ControllerAdvice implements ErrorWebExceptionHandler {

	@Autowired
	private AbstractMessageService resourceService;

	private static final ErrorServerContext ERROR_SERVER_CONTEXT = new ErrorServerContext();

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

		Mono<ServerResponse> sr;

		if (ex instanceof GenericException g) {
			sr = ServerResponse.status(g.getStatusCode())
			        .bodyValue(g.toExceptionData());
		} else {
			String eId = GenericException.uniqueId();
			Mono<String> msg = resourceService.getMessage(AbstractMessageService.UNKNOWN_ERROR_WITH_ID, eId);

			log.error("Error : {}", eId, ex);

			sr = msg.map(m -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, eId, m, ex))
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
