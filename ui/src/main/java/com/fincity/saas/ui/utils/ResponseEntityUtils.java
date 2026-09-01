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

	/**
	 * Draft responses are never cached by the browser. The live cacheAge default is
	 * seven days, which for someone iterating on the draft surface would mean their
	 * own edits appearing to have no effect.
	 */
	public static <T> Mono<ResponseEntity<T>> makeDraftResponseEntity(ObjectWithUniqueID<T> obj, String eTag) {

		return makeResponseEntity(obj, eTag, 0, null, true);
	}

	public static <T> Mono<ResponseEntity<T>> makeResponseEntity(
			ObjectWithUniqueID<T> obj, String eTag, int cacheAge, String contentType) {

		return makeResponseEntity(obj, eTag, cacheAge, contentType, false);
	}

	public static <T> Mono<ResponseEntity<T>> makeResponseEntity(
			ObjectWithUniqueID<T> obj, String eTag, int cacheAge, String contentType, boolean noStore) {

		if (eTag != null && (eTag.contains(obj.getUniqueId()) || obj.getUniqueId()
				.contains(eTag)))
			return Mono.just(ResponseEntity.status(HttpStatus.NOT_MODIFIED)
					.build());

		var rp = ResponseEntity.ok()
				.header("ETag", "W/" + obj.getUniqueId())
				.header("Cache-Control", noStore ? "no-store" : "max-age: " + cacheAge + ", must-revalidate")
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
