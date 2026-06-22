package com.modlix.saas.commons2.security.model.wallet;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * An app whose billing config carries a rent action cost (commons2 copy of the
 * commons-security model; the two stacks do not share). Returned by the
 * rent-targets endpoint so the files service can expand the owner's direct
 * managed clients, sum their stored bytes, and call charge-rent.
 */
@Data
@Accessors(chain = true)
public class RentTarget implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appCode;
    private String ownerClientCode;
}
