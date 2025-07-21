package com.fincity.security.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class UserAppAccessRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -6972847374116739149L;

    private String appCode;
    private String callbackUrl;

}
