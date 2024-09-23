package com.fincity.saas.ui.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fincity.saas.commons.model.ObjectWithUniqueID;

import reactor.core.publisher.Mono;

public class ResponseEntityUtils {

	private ResponseEntityUtils() {

	}

	public static <T> Mono<ResponseEntity<T>> makeResponseEntity(ObjectWithUniqueID<T> obj, String eTag, int cacheAge) {

		return makeResponseEntity(obj, eTag, cacheAge, null);
	}

	public static <T> Mono<ResponseEntity<T>> makeResponseEntity(
			ObjectWithUniqueID<T> obj, String eTag, int cacheAge, String contentType) {

		if (eTag != null && (eTag.contains(obj.getUniqueId()) || obj.getUniqueId()
				.contains(eTag)))
			return Mono.just(ResponseEntity.status(HttpStatus.NOT_MODIFIED)
					.build());

		var rp = ResponseEntity.ok()
				.header("ETag", "W/" + obj.getUniqueId())
				.header("Cache-Control", "max-age: " + cacheAge + ", must-revalidate")
				.header("x-frame-options", "SAMEORIGIN")
				.header("X-Frame-Options", "SAMEORIGIN");

		if (contentType != null)
			rp.contentType(org.springframework.http.MediaType.valueOf(contentType));

		if (obj.getHeaders() != null) {
			obj.getHeaders().forEach(rp::header);
		}

		return Mono.just(rp.body(obj.getObject()));
	}

}
