package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Audiences implements Serializable {

    @Serial
    private static final long serialVersionUID = -2938475610293847561L;

    private List<String> interests;
    private List<String> customAudienceIds;
    private List<String> lookalikeIds;
    private List<String> excludedIds;
}
