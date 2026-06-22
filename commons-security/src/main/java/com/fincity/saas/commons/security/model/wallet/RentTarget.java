package com.fincity.saas.commons.security.model.wallet;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * An app whose billing config carries a rent action cost (e.g. core.storage.row,
 * file.gb). Returned to the service that owns the data (core for storage, files
 * for file size) so it can enumerate the owner's direct managed clients, count
 * their usage, and call charge-rent. Code-based so it crosses the Feign boundary
 * cleanly.
 */
@Data
@Accessors(chain = true)
public class RentTarget implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appCode;
    private String ownerClientCode;
}
