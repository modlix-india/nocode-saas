package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ValidationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = -4738291056473829106L;

    private boolean valid;
    private List<ValidationIssue> issues;
}
