package com.fincity.saas.entity.processor.oserver.message.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The body of a browser-placed dial, mirroring the message service's own request type.
 *
 * <p>Mirrored rather than shared, following {@code IncomingCallRequest} and the connect-applet
 * models beside it: these two services do not depend on each other's jars, so a cross-service
 * contract is declared on both sides.
 *
 * <p>Typed rather than a {@code Map<String, Object>}, which is what this used to be. A map means the
 * key strings are the contract, and a rename on either side of the Feign call fails silently at
 * runtime with a null field rather than at compile time.
 *
 * <p>{@code toNumber} is the customer's number, read from the deal on this side. It travels in a
 * body and not a query string because a query string is retained in the gateway's access log, the
 * receiving service's access log, and every proxy in between.
 */
@Data
@Accessors(chain = true)
public class BrowserDialRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4462731509228841537L;

    private String connectionName;
    private BigInteger userId;
    private String toNumber;
}
