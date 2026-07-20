package com.modlix.saas.adzump.model.connection;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A usable platform credential resolved from the Core connection records
 * (J2 - connections). Adzump stores no tokens; this is a transient view.
 */
@Data
@Accessors(chain = true)
public class PlatformCredential implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387601L;

    private Platform platform;
    private String accessToken;
    private String accountId;
    private Map<String, String> attributes; // pageId, pixelId, mcc, etc.
    private LocalDateTime expiresAt;
}
