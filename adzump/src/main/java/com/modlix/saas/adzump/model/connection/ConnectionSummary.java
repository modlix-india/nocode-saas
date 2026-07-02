package com.modlix.saas.adzump.model.connection;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One row of the connections-list UI (GET /api/adzump/connections).
 */
@Data
@Accessors(chain = true)
public class ConnectionSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387602L;

    private Platform platform;
    private String connectionName;
    private boolean connected;
    private int accountCount;
}
