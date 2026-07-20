package com.modlix.saas.adzump.service.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.enums.Cadence;

/**
 * J14 §5.3 cadence parse: {@link SchedulePolicy} is the typed schedule view over the same
 * {@link AutonomyConfig} body as the autonomy mode/caps (schema-free — no new table). Verifies the two
 * body shapes are read and that anything absent/unparseable falls back to the conservative
 * {@link Cadence#ON_DEMAND} (an unconfigured campaign is never auto-fired on a cadence).
 */
class SchedulePolicyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesNestedScheduleCadence() {
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("schedule").put("optimizationCadence", "TWICE_DAILY");
        assertEquals(Cadence.TWICE_DAILY, SchedulePolicy.from(config(body)).optimizationCadence());
    }

    @Test
    void parsesTopLevelCadence() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("optimizationCadence", "HOURLY");
        assertEquals(Cadence.HOURLY, SchedulePolicy.from(config(body)).optimizationCadence());
    }

    @Test
    void nestedScheduleTakesPrecedenceOverTopLevel() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("optimizationCadence", "HOURLY");
        body.putObject("schedule").put("optimizationCadence", "DAILY");
        assertEquals(Cadence.DAILY, SchedulePolicy.from(config(body)).optimizationCadence());
    }

    @Test
    void absentCadenceDefaultsToOnDemand() {
        assertEquals(Cadence.ON_DEMAND, SchedulePolicy.from(config(MAPPER.createObjectNode())).optimizationCadence());
    }

    @Test
    void unparseableCadenceDefaultsToOnDemand() {
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("schedule").put("optimizationCadence", "EVERY_FORTNIGHT");
        assertEquals(Cadence.ON_DEMAND, SchedulePolicy.from(config(body)).optimizationCadence());
    }

    @Test
    void nullConfigOrBodyDefaultsToOnDemand() {
        assertEquals(Cadence.ON_DEMAND, SchedulePolicy.from(null).optimizationCadence());
        assertEquals(Cadence.ON_DEMAND, SchedulePolicy.from(new AutonomyConfig()).optimizationCadence());
    }

    private static AutonomyConfig config(ObjectNode body) {
        return new AutonomyConfig().setBody(body);
    }
}
