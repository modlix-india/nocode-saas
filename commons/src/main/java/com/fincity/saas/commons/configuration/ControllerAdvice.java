package com.fincity.saas.commons.configuration;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import jakarta.annotation.Priority;
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

	private static final Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

		logger.debug("Exception Occurred : ", ex);

		Mono<ServerResponse> sr = null;

		if (ex instanceof GenericException g) {
			sr = ServerResponse.status(g.getStatusCode())
					.bodyValue(g.toExceptionData());
		} else if (ex instanceof FeignException fe) {

			sr = handleFeignException(sr, fe);

		}

		if (sr == null) {

			sr = handleOtherExceptions(ex);
		}

		return sr.flatMap(e -> e.writeTo(exchange, ERROR_SERVER_CONTEXT));
	}

	private Mono<ServerResponse> handleOtherExceptions(Throwable ex) {
		Mono<ServerResponse> sr;
		String eId = GenericException.uniqueId();
		Mono<String> msg = resourceService.getMessage(AbstractMessageService.UNKNOWN_ERROR_WITH_ID, eId);

		final HttpStatus status = (ex instanceof ResponseStatusException rse)
				? HttpStatus.valueOf(rse.getStatusCode().value())
				: HttpStatus.INTERNAL_SERVER_ERROR;

		// The status was already being computed two lines below this, and the ERROR log sat
		// ABOVE it - so every exception was logged as a server fault with a full stack trace,
		// including the ones that are simply the client's problem.
		//
		// What that cost, measured on production 2026-09-29: of the day's ERROR lines, 848 were
		// notification's AsyncRequestNotUsableException ("Servlet container error notification
		// for disconnected client") - a browser closing an SSE tab - and 405 were security's
		// expired tokens and denied access. Together with the rest, 96% of the estate's ERROR
		// volume was not a fault, and it hid the things that were: worker's nightly transport
		// sweep had been failing on a 401 for weeks inside that noise, at one line a day.
		//
		// A 4xx is a statement about the request, not about this service. It is still logged,
		// at DEBUG with the stack, so it can be turned back on per-logger through
		// /actuator/loggers when a specific client problem needs chasing. 5xx keeps the stack
		// at ERROR, which is the only thing that should page anyone.
		if (status.is4xxClientError()) {
			log.debug("Client error {} : {}", status.value(), eId, ex);
		} else {
			log.error("Error : {}", eId, ex);
		}

		sr = msg.map(m -> new GenericException(status, eId, m, ex))
				.flatMap(g -> ServerResponse.status(g.getStatusCode())
						.bodyValue(g.toExceptionData()));
		return sr;
	}

	private Mono<ServerResponse> handleFeignException(Mono<ServerResponse> sr, FeignException fe) {
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
					sr = Mono
							.just(new GenericException(HttpStatus.valueOf(fe.status()),
									map.get("message") == null ? ""
											: map.get("message")
													.toString(),
									fe))
							.flatMap(g -> ServerResponse.status(g.getStatusCode())
									.bodyValue(g.toExceptionData()));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return sr;
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
