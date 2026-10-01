package com.fincity.saas.message.model.request.call;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * A browser-placed dial, from a service that has already checked the caller may make it. A body so the customer's
 * {@code toNumber} stays out of access logs; {@code userId} is the agent whose browser rings.
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
