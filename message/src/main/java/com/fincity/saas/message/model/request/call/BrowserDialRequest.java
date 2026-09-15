package com.fincity.saas.message.model.request.call;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * A browser-placed dial, handed over by a service that has already checked the caller may make it.
 *
 * <p>A body rather than query parameters, and that is the point of the type. {@code toNumber} is a
 * customer's phone number: in a query string it lands in the gateway's access log, this service's
 * access log, and every proxy in between, none of which are places a customer's number should be
 * retained. Its sibling {@code /internal/make} has always taken a {@code CallRequest} body for the
 * same reason.
 *
 * <p>{@code userId} is the agent whose browser should ring, not the caller's choice of agent — the
 * message service resolves it to that agent's provisioned SIP identity and refuses if they have
 * none. The tenant codes stay as parameters, matching {@code /internal/make}, because they identify
 * the caller rather than the call.
 */
@Data
@Accessors(chain = true)
@FieldNameConstants
public class BrowserDialRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5661509741260213421L;

    private String connectionName;
    private BigInteger userId;
    private String toNumber;
}
