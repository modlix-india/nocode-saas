package com.fincity.saas.multi.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

@Data
public class MultiAppUpdate implements Serializable {

    private static final long serialVersionUID = 0x09876567234234a6L;

    private String appCode;
    private Boolean isBaseUpdate;

    private Map<String, Object> transportDefinition; // NOSONAR
    private String encodedModl;
    private String transportDefinitionURL;
}
