package com.fincity.saas.commons.core.functions.email;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

/**
 * {@code address} is variadic with default {@code ""}, so a step that sets none arrives as
 * {@code [""]}. That blank must not become a recipient.
 */
class SendEmailAddressesTest {

	private static JsonArray array(Object... values) {
		JsonArray a = new JsonArray();
		for (Object v : values) a.add(v == null ? JsonNull.INSTANCE : new JsonPrimitive(v.toString()));
		return a;
	}

	@Test
	void defaultBlankIsNoAddress() {
		assertEquals(List.of(), SendEmail.givenAddresses(array("")));
	}

	@Test
	void blanksAndNullsAreDroppedAndAddressesTrimmed() {
		assertEquals(List.of("a@x.com", "b@x.com"),
				SendEmail.givenAddresses(array("", " a@x.com ", null, "   ", "b@x.com")));
	}

	@Test
	void missingArgumentIsNoAddress() {
		assertEquals(List.of(), SendEmail.givenAddresses(null));
		assertEquals(List.of(), SendEmail.givenAddresses(JsonNull.INSTANCE));
	}
}
