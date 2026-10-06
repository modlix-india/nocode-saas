package com.fincity.saas.entity.processor.oserver.message.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The body of a browser-placed dial, mirroring the message service's own request type. {@code toNumber} is the
 * deal's customer number, sent in a body so it stays out of access logs.
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
