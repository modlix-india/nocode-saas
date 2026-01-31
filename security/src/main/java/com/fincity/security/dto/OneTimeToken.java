package com.fincity.security.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class OneTimeToken extends AbstractDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = 1L;

    private ULong userId;
    private String token;
    private String ipAddress;
    private Boolean rememberMe;
}
