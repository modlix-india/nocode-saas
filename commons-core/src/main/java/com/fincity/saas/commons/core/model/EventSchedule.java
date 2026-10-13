package com.fincity.saas.commons.core.model;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.CommonsUtil;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * When an event raises itself. Sits on {@code EventDefinition.schedule}; the
 * scheduler raises the event on every tick of {@link #cron} and the same-named
 * EventAction does the work, exactly as for an event raised by CreateEvent.
 *
 * <p>
 * Every field is nullable on purpose. A client override carries only the fields
 * it changes and a null inherits the base's value, so a client can move the cron
 * or switch its own runs off without restating the rest.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EventSchedule implements Serializable {

    @Serial
    private static final long serialVersionUID = 4113940562316720318L;

    public static final String DEFAULT_TIMEZONE = "UTC";

    /**
     * Unix five-field cron ({@code min hour dom mon dow}), or Spring's six-field
     * form with seconds first. Macros such as {@code @hourly} work too.
     */
    private String cron;

    /** IANA zone the cron is read in. UTC when absent. */
    private String timezone;

    /**
     * true: one run per client holding write access to the app, each with that
     * client's own effective schedule. false: one run, for the client that holds
     * the definition.
     */
    private Boolean perClient;

    /** Absent means enabled. An override with false stops that client's runs. */
    private Boolean enabled;

    /**
     * true: each client's ticks are shifted by a fixed offset inside the period
     * (derived from app, client and event), so a perClient schedule's runs spread
     * across the interval instead of all starting on the same second. Every
     * instance derives the same offset, so the claim still dedupes. Absent or
     * false: exactly on the cron grid.
     */
    private Boolean spread;

    public EventSchedule(EventSchedule other) {
        this.cron = other.cron;
        this.timezone = other.timezone;
        this.perClient = other.perClient;
        this.enabled = other.enabled;
        this.spread = other.spread;
    }

    @JsonIgnore
    public boolean isActive() {
        return !Boolean.FALSE.equals(this.enabled) && this.cron != null && !this.cron.isBlank();
    }

    /**
     * Not named isSpread: Jackson would read that as an accessor of the
     * {@code spread} property and the @JsonIgnore would drop the field itself
     * from every API read and write.
     */
    @JsonIgnore
    public boolean isSpreadAcrossPeriod() {
        return Boolean.TRUE.equals(this.spread);
    }

    @JsonIgnore
    public boolean isForEachClient() {
        return Boolean.TRUE.equals(this.perClient);
    }

    public String effectiveTimezone() {
        return this.timezone == null || this.timezone.isBlank() ? DEFAULT_TIMEZONE : this.timezone;
    }

    /** The override's fields where it has them, the base's elsewhere. */
    public static EventSchedule merge(EventSchedule override, EventSchedule base) {

        if (override == null)
            return base == null ? null : new EventSchedule(base);
        if (base == null)
            return new EventSchedule(override);

        return new EventSchedule()
                .setCron(override.cron != null ? override.cron : base.cron)
                .setTimezone(override.timezone != null ? override.timezone : base.timezone)
                .setPerClient(override.perClient != null ? override.perClient : base.perClient)
                .setEnabled(override.enabled != null ? override.enabled : base.enabled)
                .setSpread(override.spread != null ? override.spread : base.spread);
    }

    /**
     * What an override has to store: the fields that differ from the base. Null
     * when nothing differs, so an untouched override stores no schedule at all.
     */
    public static EventSchedule difference(EventSchedule derived, EventSchedule base) {

        if (derived == null)
            return null;
        if (base == null)
            return new EventSchedule(derived);

        EventSchedule diff = new EventSchedule()
                .setCron(CommonsUtil.safeEquals(derived.cron, base.cron) ? null : derived.cron)
                .setTimezone(CommonsUtil.safeEquals(derived.timezone, base.timezone) ? null : derived.timezone)
                .setPerClient(CommonsUtil.safeEquals(derived.perClient, base.perClient) ? null : derived.perClient)
                .setEnabled(CommonsUtil.safeEquals(derived.enabled, base.enabled) ? null : derived.enabled)
                .setSpread(CommonsUtil.safeEquals(derived.spread, base.spread) ? null : derived.spread);

        if (diff.cron == null && diff.timezone == null && diff.perClient == null && diff.enabled == null
                && diff.spread == null)
            return null;

        return diff;
    }
}
