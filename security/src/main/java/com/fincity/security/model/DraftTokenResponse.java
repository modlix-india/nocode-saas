package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What the editor needs to point its preview at the draft surface.
 *
 * {@code host} is assembled here rather than in the client because the
 * environment suffix and the base domain are server configuration; a client that
 * built its own hostname would be a second place to get the environment wrong.
 * {@code token} comes back too so the heartbeat has something to extend.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DraftTokenResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String token;
    private String host;
    private LocalDateTime expiresAt;
}
