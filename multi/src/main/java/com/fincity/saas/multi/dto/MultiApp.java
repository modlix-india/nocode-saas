package com.fincity.saas.multi.dto;

import java.io.Serializable;
import java.util.Map;

import org.jooq.types.ULong;

import lombok.Data;

@Data
public class MultiApp implements Serializable {

    private static final long serialVersionUID = 0x09876567876acd876L;

    private ULong appId;
    private String appCode;
    private String appName;
    private ULong clientId;
    private String clientCode;
    private String appAccessType;
    private String appType;

    private Map<String, Object> transportDefinition; // NOSONAR
    private String transportDefinitionURL;
}
