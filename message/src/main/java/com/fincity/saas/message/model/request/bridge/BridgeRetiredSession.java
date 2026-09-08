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
 * A session the bridge has given up on and whose slot it has reclaimed.
 *
 * <p>Reported rather than inferred, and acknowledged rather than assumed delivered. Until this
 * service releases the assignment, a customer who comes back is still pinned to the instance that
 * just discarded them, which is precisely the "instances clogged with dead numbers" problem
 * retirement exists to solve.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeRetiredSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 8877124430950625141L;

    private String sessionId;

    /** The state it died in. Worth keeping: a rise in the banned bucket is the signal that matters. */
    private WhatsappSessionState state;

    private String reason;
    private String phone;

    /** When the bridge retired it. Instant, because Go sends RFC 3339 with an offset. */
    private Instant at;
}
