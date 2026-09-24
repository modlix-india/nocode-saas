package com.fincity.saas.commons.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The blueprint has to override the way properties do, key by key, and not the
 * way title does, as a whole value. A tenant that restates one entry must keep
 * receiving base corrections for every other entry, which is the entire reason
 * the plan is a map of uid-keyed entries rather than a list.
 */
class AbstractOverridableDTOBlueprintTest {

	/** The smallest possible concrete subclass: the base class is what is under test. */
	static class Thing extends AbstractOverridableDTO<Thing> {

		private static final long serialVersionUID = 1L;

		Thing() {
			// A plain subclass, so the base class clone() is what gets exercised.
		}

		Thing(Thing other) {
			super(other);
		}

		@Override
		public Mono<Thing> applyOverride(Thing base) {
			return Mono.just(this);
		}

		@Override
		public Mono<Thing> extractDifference(Thing base) {
			return Mono.just(this);
		}
	}

	private static Map<String, Object> entry(int order, String purpose) {
		Map<String, Object> m = new HashMap<>();
		m.put("order", order);
		m.put("purpose", purpose);
		return m;
	}

	private static Map<String, Object> plan(Map<String, Object> sections) {
		Map<String, Object> inner = new HashMap<>();
		inner.put("sections", sections);
		Map<String, Object> outer = new HashMap<>();
		outer.put("plan", inner);
		return outer;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> sectionsOf(Map<String, Object> blueprint) {
		return (Map<String, Object>) ((Map<String, Object>) blueprint.get("plan")).get("sections");
	}

	@Test
	void overrideKeepsTheEntriesTheTenantDidNotTouch() {

		Map<String, Object> baseSections = new HashMap<>();
		baseSections.put("aHero", entry(1000, "Base hero"));
		baseSections.put("aForm", entry(2000, "Base form"));

		Thing base = new Thing();
		base.setBlueprint(plan(baseSections));

		Map<String, Object> tenantSections = new HashMap<>();
		tenantSections.put("aHero", entry(1000, "Tenant hero"));

		Thing tenant = new Thing();
		tenant.setBlueprint(plan(tenantSections));

		StepVerifier.create(tenant.applyActualOverride(base))
				.assertNext(t -> {
					Map<String, Object> merged = sectionsOf(t.getBlueprint());
					assertEquals(2, merged.size(), "the untouched base entry must survive");
					assertEquals("Tenant hero", ((Map<?, ?>) merged.get("aHero")).get("purpose"));
					assertEquals("Base form", ((Map<?, ?>) merged.get("aForm")).get("purpose"));
				})
				.verifyComplete();
	}

	@Test
	void extractingAnOverrideCarriesOnlyWhatChanged() {

		Map<String, Object> baseSections = new HashMap<>();
		baseSections.put("aHero", entry(1000, "Base hero"));
		baseSections.put("aForm", entry(2000, "Base form"));

		Thing base = new Thing();
		base.setBlueprint(plan(baseSections));

		Map<String, Object> editedSections = new HashMap<>();
		editedSections.put("aHero", entry(1000, "Tenant hero"));
		editedSections.put("aForm", entry(2000, "Base form"));

		Thing edited = new Thing();
		edited.setBlueprint(plan(editedSections));

		StepVerifier.create(edited.makeActualOverride(base))
				.assertNext(t -> {
					Map<String, Object> diff = sectionsOf(t.getBlueprint());
					// This is the assertion that matters. If the whole map were carried,
					// aForm would be here, and a later base correction to it would never
					// reach this tenant again, with nothing anywhere reporting a problem.
					assertTrue(diff.containsKey("aHero"), "the changed entry belongs in the override");
					assertTrue(!diff.containsKey("aForm") || diff.get("aForm") == null
							|| ((Map<?, ?>) diff.get("aForm")).isEmpty(),
							"the unchanged entry must not be copied into the override");
				})
				.verifyComplete();
	}

	@Test
	void anObjectWithNoPlanStaysAnObjectWithNoPlan() {

		Thing base = new Thing();
		Thing tenant = new Thing();

		StepVerifier.create(tenant.applyActualOverride(base))
				.assertNext(t -> assertNull(t.getBlueprint()))
				.verifyComplete();
	}

	@Test
	void aTenantWithNoPlanInheritsTheBasePlan() {

		Map<String, Object> baseSections = new HashMap<>();
		baseSections.put("aHero", entry(1000, "Base hero"));

		Thing base = new Thing();
		base.setBlueprint(plan(baseSections));

		Thing tenant = new Thing();

		StepVerifier.create(tenant.applyActualOverride(base))
				.assertNext(t -> assertEquals(1, sectionsOf(t.getBlueprint()).size()))
				.verifyComplete();
	}

	@Test
	void cloneCarriesTheBlueprintAndDeepCopiesIt() {

		// The step that is easiest to leave out and fails silently: without it the
		// plan vanishes on every copy, override and transport path while everything
		// still compiles and still answers 200.
		Map<String, Object> sections = new HashMap<>();
		sections.put("aHero", entry(1000, "Base hero"));

		Thing original = new Thing();
		original.setName("thing").setBlueprint(plan(sections));

		Thing copy = new Thing(original);

		assertEquals("Base hero",
				((Map<?, ?>) sectionsOf(copy.getBlueprint()).get("aHero")).get("purpose"));
		assertNotSame(original.getBlueprint(), copy.getBlueprint(), "a shared reference is not a clone");

		sectionsOf(original.getBlueprint()).put("aLater", entry(3000, "Added after the copy"));
		assertEquals(1, sectionsOf(copy.getBlueprint()).size(), "the copy must not see a later edit");
	}
}
