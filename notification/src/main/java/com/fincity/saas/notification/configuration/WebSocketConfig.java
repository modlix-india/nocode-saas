package com.fincity.saas.notification.configuration;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebSocketMessageBroker
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
