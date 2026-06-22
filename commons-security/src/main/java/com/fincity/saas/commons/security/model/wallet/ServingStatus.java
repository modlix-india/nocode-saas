package com.fincity.saas.commons.security.model.wallet;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Runtime serving decision for an (app, client): whether the app should be
 * replaced by a suspend app because the serving client's wallet is suspended,
 * and which suspend app to serve in its place. Platform/SYSTEM-owned apps are
 * never suspended.
 */
@Data
@Accessors(chain = true)
public class ServingStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean suspended;
    private String suspendAppCode;
    private String suspendClientCode;
}
