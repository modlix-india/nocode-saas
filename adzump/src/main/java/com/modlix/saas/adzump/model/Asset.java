package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Asset implements Serializable {

    @Serial
    private static final long serialVersionUID = -1056473829105647382L;

    private String kind;
    private String assetId;
    private String url;
    private String hash;
}
