package com.fincity.security.model;

import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AppDependency implements Serializable {

    private String appName;
    private String appCode;
    private ULong appId;

    private String dependentAppName;
    private String dependentAppCode;
    private ULong dependentAppId;
}
