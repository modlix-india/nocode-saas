package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AssetGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1938475610293847562L;

    private String id;
    private Platform platform;
    private List<String> headlines;
    private List<String> descriptions;
    private List<String> images;
    private List<String> videos;
    private List<String> logos;
    private Audiences audienceSignals;
    private List<JsonNode> listingGroups;
}
