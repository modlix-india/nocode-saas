package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LeadForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 5647382910564738292L;

    private String id;
    private Platform platform;
    private List<LeadFormField> fields;
    private String privacyPolicyUrl;
    private String thankYouMessage;
}
