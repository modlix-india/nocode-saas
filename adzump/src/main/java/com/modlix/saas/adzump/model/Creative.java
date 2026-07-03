package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.modlix.saas.adzump.enums.CreativeFormat;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Creative implements Serializable {

    @Serial
    private static final long serialVersionUID = -8291056473829105647L;

    private String id;
    private CreativeFormat format;
    private List<Asset> assets;
    private Copy copy;
    private Map<String, String> attributes;
    private Double predictScore;
    private String source;
}
