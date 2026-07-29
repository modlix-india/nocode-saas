package com.modlix.saas.adzump.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PlanCompleteness implements Serializable {

    @Serial
    private static final long serialVersionUID = 7382910564738291058L;

    private boolean complete;
    private List<String> missingRequired;
    private Map<String, Boolean> slots;
}
