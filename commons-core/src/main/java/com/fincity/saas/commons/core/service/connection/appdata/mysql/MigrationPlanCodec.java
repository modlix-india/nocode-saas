package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Stores the plan in the journal row, so recovery replays what was running rather
 * than guessing at it.
 *
 * Re-planning after a crash does NOT reproduce the original plan, and the difference
 * is worst exactly where it matters. Mid-CONTRACT the old column has been dropped and
 * the temporary one is not yet renamed; the inspector hides temporary columns on
 * purpose, so a fresh diff sees neither and plans a simple ADD COLUMN. That plan has
 * different steps, so the recorded statement index points somewhere meaningless, and
 * replaying from it would run the wrong statement.
 *
 * A journal that does not record what it was doing is not a journal. The plan is a
 * handful of statements, so storing it costs nothing and makes recovery need nothing
 * but the row itself - which matters, because recovery runs precisely when the rest
 * of the system is in a state nobody planned.
 */
public final class MigrationPlanCodec {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<List<MigrationStep>> STEPS = new TypeReference<>() {};

    private MigrationPlanCodec() {
    }

    public static String write(List<MigrationStep> plan) {
        try {
            return MAPPER.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a migration plan could not be recorded", e);
        }
    }

    /**
     * The plan back, or empty when the row predates this column or holds something
     * unreadable.
     *
     * Empty rather than an exception: a row that cannot be replayed should be
     * reported and left alone, not turned into a sweep that fails on every pass.
     */
    public static List<MigrationStep> read(String json) {

        if (json == null || json.isBlank()) return List.of();

        try {
            List<MigrationStep> steps = MAPPER.readValue(json, STEPS);
            return steps == null ? List.of() : steps;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
