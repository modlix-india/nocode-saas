package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Ad implements Serializable {

    @Serial
    private static final long serialVersionUID = -4756102938475610294L;

    private String id;
    private String name;
    private String creativeId;
    private String finalUrl;
    private String leadFormId;
    private String callToAction;
    private List<String> headlines;
    private List<String> descriptions;
}
