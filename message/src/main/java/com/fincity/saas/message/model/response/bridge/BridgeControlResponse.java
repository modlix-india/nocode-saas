package com.fincity.saas.message.model.response.bridge;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What the control plane returns to both registration and heartbeat.
 *
 * <p>The response is the only channel this service has into a bridge host. Nothing reaches into
 * Mumbai: no SSH, no inbound port, no key distribution across the region boundary. Everything the
 * fleet is told, it is told here, on a call it made itself.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BridgeControlResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 6641228853109340178L;

    /**
     * The image this instance should be running.
     *
     * <p>This is the deployment mechanism. CI declares a desired image and the host agent rolls the
     * instance when what is running differs, which is what lets a rollout be strictly serial and
     * gated on re-registration rather than on a port answering.
     */
    private String desiredImage;

    /**
     * Take no new sessions.
     *
     * <p>Existing ones keep working. Set ahead of a deploy so the roll drains rather than
     * parallel-runs, which is not a preference: two processes on one device store corrupt the Signal
     * ratchet, and every customer on the instance would have to re-scan a QR code.
     */
    private boolean draining;

    /**
     * Session ids whose retirement this service has recorded and released.
     *
     * <p>Echoed so the instance can stop resending them. Anything not echoed is retried on the next
     * heartbeat, so a dropped response costs a repeat rather than a lost release.
     */
    private List<String> acceptedRetirements = new ArrayList<>();
}
