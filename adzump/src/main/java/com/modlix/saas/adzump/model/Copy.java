package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Copy implements Serializable {

    @Serial
    private static final long serialVersionUID = 2910564738291056473L;

    private List<String> headlines;
    private List<String> primaryTexts;
    private List<String> descriptions;
    private String cta;
}
