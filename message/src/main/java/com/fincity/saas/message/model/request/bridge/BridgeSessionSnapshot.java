package com.fincity.saas.message.model.request.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fincity.saas.message.enums.bridge.WhatsappSessionState;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One session as the bridge sees it.
 *
 * <p>Cross-service contract, and the receiving half of the Go {@code session.Snapshot}. Field names
 * match that struct's JSON tags exactly; a rename on either side arrives as a silent null here,
 * which for {@code state} would report every session as unknown while both services returned 200.
 *
 * <p>Unknown properties are ignored so the bridge can add a field without this failing to
 * deserialise. The reverse is not tolerable and is not silent: an unknown {@code state} value fails
 * loudly, because quietly mapping a new terminal state to null would leave a dead session looking
 * live.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeSessionSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1043391558022517704L;

    /** The session id this service assigned at create, which is the phone number row's code. */
    private String id;

    private String appCode;
    private String clientCode;

    private WhatsappSessionState state;
    private String reason;

    private String phone;
    private String country;

    /**
     * Instant, not LocalDateTime, and that is not a style choice.
     *
     * <p>Go marshals {@code time.Time} as RFC 3339 with an offset ({@code 2026-08-06T10:30:00Z}),
     * and Jackson's LocalDateTime deserializer parses ISO_LOCAL_DATE_TIME, which has no offset and
     * rejects the trailing Z. Declaring these as LocalDateTime compiles, reads correctly to a
     * reviewer, and fails at runtime on the first heartbeat carrying a linked session. Converted to
     * UTC LocalDateTime at the persistence boundary, where the servers already run UTC.
     */
    private Instant linkedAt;

    /**
     * When the state last changed.
     *
     * <p>Separate from any updated-at so a dead session cannot reset its own retirement clock simply
     * by being reported again on every heartbeat.
     */
    private Instant stateSince;

    private Instant bannedUntil;

    private Integer sentLastHour;
    private Integer reconnects;
    private String lastError;

    /**
     * Whether a handset ever completed a scan on this session, read by the bridge from its device
     * store rather than from any timestamp.
     *
     * <p>Carried because {@code linkedAt} cannot answer the question on its own: it is absent both
     * for a session that never paired and, before the bridge started restoring it, for every session
     * on an instance that had restarted. The bridge's own reaper turns on this distinction, and it is
     * worth having on this side too, so a row in the fleet view that has never had a device behind it
     * is visibly that rather than looking like an offline customer.
     */
    private Boolean paired;
}
