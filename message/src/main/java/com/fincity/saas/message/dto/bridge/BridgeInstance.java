package com.fincity.saas.message.dto.bridge;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.message.enums.bridge.BridgeInstanceState;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * One WhatsApp bridge instance in the fleet.
 *
 * <p>Extends {@link AbstractUpdatableDTO} rather than this service's {@code BaseUpdatableDto},
 * which is the one place in the schema where that is right. Every other table here is tenant data
 * and carries an app and client code; an instance is infrastructure that holds sessions for many
 * tenants and belongs to none, so those columns would be a lie in every row and a tenant-scoped
 * query over them would quietly hide most of the fleet at exactly the wrong moment.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class BridgeInstance extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 4471553288845163927L;

    /** The instance's own id. Stable across restarts, because it also keys its device store volume. */
    private String instanceId;

    /**
     * Where this service calls the instance.
     *
     * <p>Reported by the instance rather than derived from the request it registered with, because
     * it sits behind the per-country ingress and the address we must call is not the address it
     * appears to come from.
     */
    private String baseUrl;

    /** ISO-3166 alpha-2 codes, comma separated, as stored. Read through {@link #countrySet()}. */
    private String countries;

    /**
     * Policy limit on live sessions, an order of magnitude below what the hardware allows.
     *
     * <p>Set by IP reputation and blast radius, not by RAM: many numbers behind one address is
     * itself the coordinated-spam pattern, and one restart taking fifty sessions down produces a
     * simultaneous reconnect storm that is its own anomaly.
     */
    private Integer sessionCap;

    /** Build actually running, which is what makes a rollout observable rather than assumed. */
    private String version;

    private BridgeInstanceState state = BridgeInstanceState.UP;

    /** Image this instance should be running. The deployment channel, read back on each heartbeat. */
    private String desiredImage;

    /** Excludes terminal sessions, because that is what the cap is about. */
    private Integer activeSessions = 0;

    /** Everything held, including terminal sessions awaiting the reaper. */
    private Integer heldSessions = 0;

    private LocalDateTime lastHeartbeatAt;
    private Integer missedHeartbeats = 0;
    private String lastError;

    private boolean isActive = Boolean.TRUE;

    /** Parsed country codes, upper-cased and de-duplicated, preserving declaration order. */
    public Set<String> countrySet() {
        if (this.countries == null || this.countries.isBlank()) return Set.of();

        return Arrays.stream(this.countries.split(","))
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean serves(String country) {
        return country != null && this.countrySet().contains(country.toUpperCase(Locale.ROOT));
    }

    /**
     * Whether a new session may be placed here.
     *
     * <p>Measured against {@code activeSessions} rather than everything held, so a run of bans does
     * not close an instance to signups for the days before the reaper clears them.
     */
    public boolean hasRoom() {
        int cap = this.sessionCap == null ? 0 : this.sessionCap;
        int held = this.activeSessions == null ? 0 : this.activeSessions;
        return cap > 0 && held < cap;
    }

    public boolean placeable(String country) {
        return this.isActive && this.state == BridgeInstanceState.UP && this.serves(country) && this.hasRoom();
    }
}
