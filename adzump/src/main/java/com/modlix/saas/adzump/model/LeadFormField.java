package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LeadFormField implements Serializable {

    @Serial
    private static final long serialVersionUID = -7382910564738291057L;

    private String key;
    private String type;
    private List<String> options;
}
