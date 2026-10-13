package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.functions.events.ScheduleEventFunction;
import com.fincity.saas.commons.core.model.EventSchedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

/** How a schedule folds down the override chain, which is what makes a client's own schedule win. */
class EventScheduleOverrideTest {

    private static EventDefinition definition(EventSchedule schedule) {
        EventDefinition ed = new EventDefinition();
        ed.setName("outboxSweep");
        ed.setAppCode("leadzumptwo");
        ed.setSchedule(schedule);
        return ed;
    }

    private static EventSchedule base() {
        return new EventSchedule().setCron("* * * * *").setTimezone("UTC").setPerClient(true).setEnabled(true);
    }

    @Test
    void anOverrideKeepsTheBaseFieldsItDoesNotSet() {

        EventDefinition merged = definition(new EventSchedule().setCron("*/5 * * * *"))
                .applyOverride(definition(base()))
                .block();

        assertEquals("*/5 * * * *", merged.getSchedule().getCron());
        assertEquals("UTC", merged.getSchedule().getTimezone());
        assertTrue(merged.getSchedule().isForEachClient());
        assertTrue(merged.getSchedule().isActive());
    }

    @Test
    void anOverrideCanSwitchOnlyItsOwnRunsOff() {

        EventDefinition merged = definition(new EventSchedule().setEnabled(false))
                .applyOverride(definition(base()))
                .block();

        assertFalse(merged.getSchedule().isActive());
        assertEquals("* * * * *", merged.getSchedule().getCron());
    }

    @Test
    void anOverrideWithoutAScheduleInheritsTheBaseOne() {

        EventDefinition merged = definition(null).applyOverride(definition(base())).block();

        assertEquals(base(), merged.getSchedule());
    }

    @Test
    void savingAnOverrideStoresOnlyWhatDiffers() {

        EventSchedule edited = base().setTimezone("Asia/Kolkata");
        EventDefinition stored = definition(edited).extractDifference(definition(base())).block();

        assertEquals(new EventSchedule().setTimezone("Asia/Kolkata"), stored.getSchedule());

        EventDefinition untouched = definition(base()).extractDifference(definition(base())).block();
        assertNull(untouched.getSchedule());
    }

    @Test
    void theCopyConstructorDoesNotShareTheSchedule() {

        EventDefinition original = definition(base());
        EventDefinition copy = new EventDefinition(original);
        copy.getSchedule().setCron("0 0 * * *");

        assertEquals("* * * * *", original.getSchedule().getCron());
    }

    @Test
    void runAtTakesIsoTimestampsAndEpochMillis() {

        assertEquals(Instant.parse("2026-10-09T03:30:00Z"),
                ScheduleEventFunction.parseRunAt(new JsonPrimitive("2026-10-09T09:00:00+05:30")));
        assertEquals(Instant.parse("2026-10-09T09:00:00Z"),
                ScheduleEventFunction.parseRunAt(new JsonPrimitive("2026-10-09T09:00:00Z")));
        assertEquals(Instant.parse("2026-10-09T09:00:00Z"),
                ScheduleEventFunction.parseRunAt(new JsonPrimitive("2026-10-09T09:00:00")));
        assertEquals(Instant.ofEpochMilli(1_791_000_000_000L),
                ScheduleEventFunction.parseRunAt(new JsonPrimitive(1_791_000_000_000L)));
        assertTrue(ScheduleEventFunction.parseRunAt(JsonNull.INSTANCE)
                .isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void spreadSurvivesTheApiAndInheritsLikeTheOtherFields() throws Exception {

        // The API reads and writes definitions with Jackson; a helper named isSpread()
        // once made Jackson drop the field on every save.
        ObjectMapper mapper = new ObjectMapper();
        EventSchedule base = new EventSchedule().setCron("*/5 * * * *").setPerClient(true).setSpread(true);
        EventSchedule back = mapper.readValue(mapper.writeValueAsString(base), EventSchedule.class);
        assertEquals(Boolean.TRUE, back.getSpread());
        assertTrue(back.isSpreadAcrossPeriod());
        assertTrue(mapper.writeValueAsString(base).contains("\"spread\":true"));

        // An override without spread inherits it; one that turns it off wins.
        assertTrue(EventSchedule.merge(new EventSchedule().setEnabled(true), base).isSpreadAcrossPeriod());
        assertFalse(EventSchedule.merge(new EventSchedule().setSpread(false), base).isSpreadAcrossPeriod());
        assertEquals(Boolean.FALSE,
                EventSchedule.difference(new EventSchedule(base).setSpread(false), base).getSpread());
    }
}
