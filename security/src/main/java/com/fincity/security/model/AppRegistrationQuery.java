package com.fincity.security.model;

import java.io.Serializable;

import org.jooq.types.ULong;

import com.fincity.security.enums.ClientLevelType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AppRegistrationQuery implements Serializable {

    private static final long serialVersionUID = 0x222abc2L;

    private String clientCode;
    private ULong clientId;
    private String clientType;
    private ClientLevelType level;
    private String businessType;
    private ULong integrationId;
    private Integer page;
    private Integer size;
}
