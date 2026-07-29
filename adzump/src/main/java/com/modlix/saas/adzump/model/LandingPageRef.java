package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LandingPageRef implements Serializable {

    @Serial
    private static final long serialVersionUID = 3829105647382910565L;

    private String appCode;
    private String pageName;
    private String url;
    private Boolean instrumented;
    private List<String> attributionParams;
}
