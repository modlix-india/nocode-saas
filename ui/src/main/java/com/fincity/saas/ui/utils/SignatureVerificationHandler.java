package com.fincity.saas.ui.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import reactor.core.publisher.Mono;

public class SignatureVerificationHandler implements HttpHandler {

	private String encodingAlgorithm = "HmacSHA256";
	private String secretKey = "someSecretKeyThatShouldBeSecure";
	private String headerThatContainsSignature = "X-Signature-SHA256";

	private boolean verifySignature(String payload, String signature) throws NoSuchAlgorithmException, InvalidKeyException {
		var sha256_HMAC = Mac.getInstance(encodingAlgorithm);
		SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), encodingAlgorithm);
		sha256_HMAC.init(secretKeySpec);

		byte[] hash = sha256_HMAC.doFinal(payload.getBytes());
		String message = Base64.getEncoder().encodeToString(hash);

		System.out.println("Payload : " + payload);
		System.out.println("Message : " + message);
		System.out.println("Signature : " + signature);

		return message.equals(signature);
	}

	@Override
	public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
		return null;
	}

//	private String readBody(HttpExchange httpExchange) throws IOException {
//		BufferedInputStream stream = new BufferedInputStream(httpExchange);
//		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
//		for (int result = stream.read(); result != -1; result = stream.read()) {
//			byteBuffer.write((byte) result);
//		}
//		return byteBuffer.toString(StandardCharsets.UTF_8);
//	}
//
//	private void returnWithStatus(HttpExchange httpExchange, int httpStatusCode) throws IOException {
//		httpExchange.sendResponseHeaders(httpStatusCode, 0);
//		httpExchange.getResponseBody().close();
//	}
}
