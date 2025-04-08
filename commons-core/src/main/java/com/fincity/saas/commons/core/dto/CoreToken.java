package com.fincity.saas.commons.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.commons.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class CoreToken extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 6585106241902821249L;

    private ULong userId;
    private String clientCode;
    private String appCode;
    private String connectionName;
    private CoreTokensTokenType tokenType;
    private String token;
    private String state;
    private JsonNode tokenMetadata;
    private Boolean isRevoked;
    private Boolean isLifetimeToken;
    private LocalDateTime expiresAt;
}
