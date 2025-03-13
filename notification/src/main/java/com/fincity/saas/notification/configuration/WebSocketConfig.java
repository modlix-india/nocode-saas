package com.fincity.saas.notification.configuration;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

@Configuration
public class WebSocketConfig implements WebSocketHandler {

	@Override
	public List<String> getSubProtocols() {
		return WebSocketHandler.super.getSubProtocols();
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return null;
	}
}
