package com.fincity.security.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * An editing session's grant of the draft surface, carried as its own hostname.
 *
 * {@code token} is the 32 hex characters only. The hostname the browser uses is
 * {@code t-<token><appCodeSuffix>.modlix.com}, assembled at mint time and taken
 * apart again when the gateway resolves it, so the environment suffix is never
 * stored and a configuration change cannot strand a row.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class DraftToken extends AbstractDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String token;
    private String appCode;
    private ULong clientId;
    private ULong userId;
    private LocalDateTime expiresAt;
}
