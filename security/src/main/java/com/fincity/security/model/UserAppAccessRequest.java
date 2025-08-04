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
    private static final long serialVersionUID = -3594048465034362759L;

    private String appCode;
    private String callbackUrl;

    private String requestId;
    private ULong profileId;
}
