package com.fincity.security.configuration;

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

import com.fincity.security.exception.GenericException;
import com.fincity.security.service.MessageResourceService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
@Priority(0)
public class ControllerAdvice implements ErrorWebExceptionHandler {

	@Autowired
	private MessageResourceService resourceService;

	private static final ErrorServerContext ERROR_SERVER_CONTEXT = new ErrorServerContext();

//	@ExceptionHandler(GenericException.class)
//	public Mono<ResponseEntity<GenericExceptionData>> handleGenericException(GenericException e) {
//
//		log.debug(e.getMessage(), e);
//		return Mono.just(new ResponseEntity<>(e.toExceptionData(), new HttpHeaders(), e.getStatusCode()));
//	}
//
//	@ExceptionHandler(Exception.class)
//	public Mono<ResponseEntity<GenericExceptionData>> handleGenericException(Exception e) {
//
//		String eId = GenericException.uniqueId();
//		Mono<String> msg = resourceService.getMessage(MessageResourceService.UNKNOWN_ERROR_WITH_ID, eId);
//
//		log.error("Error : {}", eId, e);
//
//		return msg.map(m -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, eId, m, e))
//		        .flatMap(this::handleGenericException);
//	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

		Mono<ServerResponse> sr;

		if (ex instanceof GenericException g) {
			sr = ServerResponse.status(g.getStatusCode())
			        .bodyValue(g.toExceptionData());
		} else {
			String eId = GenericException.uniqueId();
			Mono<String> msg = resourceService.getMessage(MessageResourceService.UNKNOWN_ERROR_WITH_ID, eId);

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
