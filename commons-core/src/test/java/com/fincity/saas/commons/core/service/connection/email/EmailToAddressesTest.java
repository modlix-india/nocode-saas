package com.fincity.saas.commons.core.service.connection.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Blank addresses are not recipients, wherever they come from, and a list of nothing but blanks
 * meets the "No Send Addresses Found." guard instead of slipping past it.
 */
class EmailToAddressesTest {

	private final SMTPService service = new SMTPService() {
		{
			this.msgService = new CoreMessageResourceService();
		}
	};

	private Mono<List<String>> to(List<String> given, String toExpression) {
		Template template = new Template();
		template.setToExpression(toExpression);
		return service.getToAddresses(given, template, Map.of("email", "c@x.com"));
	}

	@Test
	void blankGivenIsDroppedBeforeMergingWithTheTemplate() {
		StepVerifier.create(to(new ArrayList<>(List.of("")), "${email}"))
				.expectNext(List.of("c@x.com"))
				.verifyComplete();
	}

	@Test
	void givenAndTemplateAddressesMerge() {
		StepVerifier.create(to(List.of("a@x.com", " "), "${email}; d@x.com"))
				.expectNext(List.of("a@x.com", "c@x.com", "d@x.com"))
				.verifyComplete();
	}

	@Test
	void onlyBlanksAndNoTemplateHitsTheGuard() {
		StepVerifier.create(to(List.of(""), null))
				.expectError(GenericException.class)
				.verify();
	}

	@Test
	void givenWithoutTemplateIsKept() {
		StepVerifier.create(to(List.of("a@x.com"), ""))
				.expectNext(List.of("a@x.com"))
				.verifyComplete();
	}
}
