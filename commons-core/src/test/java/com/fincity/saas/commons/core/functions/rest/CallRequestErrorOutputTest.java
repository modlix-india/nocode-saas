package com.fincity.saas.commons.core.functions.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import feign.FeignException;
import feign.Request;
import feign.Response;

/**
 * A failed call emits ERROR and a paired output. Both must carry a numeric statusCode (the
 * signature declares a number); the paired output used to send {@code statusCode {}}.
 */
class CallRequestErrorOutputTest {

	private final CallRequest fn = new CallRequest(null, null, null, "CallRequest", "GET", false, new Gson());

	private static List<EventResult> events(FunctionOutput fo) {
		EventResult first = fo.next();
		EventResult second = fo.next();
		return List.of(first, second);
	}

	private static void assertStatus(List<EventResult> events, int status) {
		assertEquals(Event.ERROR, events.get(0).getName());
		assertEquals(Event.OUTPUT, events.get(1).getName());
		for (EventResult e : events) {
			JsonElement code = e.getResult().get("statusCode");
			assertTrue(code.isJsonPrimitive() && code.getAsJsonPrimitive().isNumber(),
					e.getName() + " statusCode is not a number: " + code);
			assertEquals(status, code.getAsInt(), e.getName());
		}
	}

	@Test
	void httpErrorCarriesTheRealStatusOnBothEvents() {
		FunctionOutput fo = fn.makeErrorResponseFunctionOutput(
				new RestResponse().setStatus(404).setData("missing").setHeaders(Map.of())).block();

		assertStatus(events(fo), 404);
	}

	@Test
	void exceptionWithoutResponseReportsNoResponseStatus() {
		FunctionOutput fo = fn.makeExceptionResponseFunctionOutput(new TimeoutException("slow")).block();

		List<EventResult> events = events(fo);
		assertStatus(events, CallRequest.NO_RESPONSE_STATUS);
		assertEquals("slow", events.get(0).getResult().get("data").getAsString());
	}

	@Test
	void feignExceptionReportsItsOwnStatus() {
		Request request = Request.create(Request.HttpMethod.GET, "http://files/x", Map.of(), null,
				StandardCharsets.UTF_8, null);
		FeignException fe = FeignException.errorStatus("get", Response.builder()
				.status(403)
				.reason("Forbidden")
				.request(request)
				.headers(Map.of())
				.build());

		assertStatus(events(fn.makeExceptionResponseFunctionOutput(fe).block()), 403);
	}
}
