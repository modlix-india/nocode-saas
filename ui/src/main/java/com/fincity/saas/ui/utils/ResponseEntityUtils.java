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

		return build(obj, eTag, null, "no-store");
	}

	/**
	 * For responses whose body depends on who is asking: page definitions (the
	 * login page instead of a protected page for an anonymous visitor) and the
	 * application definition (its inlined shell page, or the forbidden page).
	 *
	 * `no-cache` lets the browser keep the body but makes it ask before every use,
	 * so the ETag still answers with a 304 and the body is not sent again. A
	 * freshness lifetime would instead let the browser reuse a logged-in
	 * definition without asking for as long as the lifetime runs, which is what
	 * kept a page open after the token was cleared, and what kept an edited page
	 * stale for a week.
	 */
	public static <T> Mono<ResponseEntity<T>> makeRevalidateResponseEntity(ObjectWithUniqueID<T> obj,
			String eTag) {

		return build(obj, eTag, null, CACHE_CONTROL_REVALIDATE);
	}

	public static final String CACHE_CONTROL_REVALIDATE = "no-cache";

	public static <T> Mono<ResponseEntity<T>> makeResponseEntity(
			ObjectWithUniqueID<T> obj, String eTag, int cacheAge, String contentType) {

		return makeResponseEntity(obj, eTag, cacheAge, contentType, false);
	}

	public static <T> Mono<ResponseEntity<T>> makeResponseEntity(
			ObjectWithUniqueID<T> obj, String eTag, int cacheAge, String contentType, boolean noStore) {

		// "max-age=" and not "max-age: ". A cache directive is `token [ "=" value ]`
		// (RFC 9111 5.2), so the colon form parses as an unknown directive and is
		// dropped: the response then carried no freshness lifetime at all and every
		// one of these was revalidated on every navigation, the style sheet included.
		return build(obj, eTag, contentType, noStore ? "no-store" : "max-age=" + cacheAge + ", must-revalidate");
	}

	private static <T> Mono<ResponseEntity<T>> build(ObjectWithUniqueID<T> obj, String eTag, String contentType,
			String cacheControl) {

		if (eTag != null && (eTag.contains(obj.getUniqueId()) || obj.getUniqueId()
				.contains(eTag)))
			// Cache-Control on the 304 too (RFC 9110 15.4.5): a browser updates its
			// stored headers from the ones on a 304, so without it an entry stored
			// under an older, longer lifetime keeps that lifetime every time it is
			// revalidated.
			return Mono.just(ResponseEntity.status(HttpStatus.NOT_MODIFIED)
					.header("Cache-Control", cacheControl)
					.build());

		var rp = ResponseEntity.ok()
				.header("ETag", "W/" + obj.getUniqueId())
				.header("Cache-Control", cacheControl)
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
