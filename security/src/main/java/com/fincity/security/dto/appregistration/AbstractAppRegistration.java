package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.enums.ClientLevelType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractAppRegistration extends AbstractDTO<ULong, ULong> {

    private ULong clientId;
    private ULong appId;
    private String clientType;
    private ClientLevelType level;
    private String businessType = "COMMON";

    // Extras required for UI
    private Client client;
    private App app;
}
