package com.fincity.security.dto;

import java.io.Serializable;

import org.jooq.types.ULong;

import com.fincity.security.enums.ClientLevelType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class AbstractAppRegistration implements Serializable {

    private ULong clientId;
    private ULong appId;
    private String clientType;
    private ClientLevelType level;
    private String businessType;

    // Extras required for UI
    private Client client;
    private App app;
}
