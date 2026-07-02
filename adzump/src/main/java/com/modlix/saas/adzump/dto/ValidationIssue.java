package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ValidationIssue implements Serializable {

    @Serial
    private static final long serialVersionUID = 2910564738291056475L;

    private String code;
    private Severity severity;
    private String field;
    private String message;
}
