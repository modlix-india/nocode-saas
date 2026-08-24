package com.fincity.saas.message.model.request.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A batch of events drained from one bridge's local outbox.
 *
 * <p>Batched rather than one call per event because the hop crosses regions at roughly 200ms, and a
 * backlog of a few hundred events would otherwise take minutes to clear on round trips alone.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeEventsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 7712354419946120845L;

    private String instanceId;

    private List<BridgeEvent> events;
}
