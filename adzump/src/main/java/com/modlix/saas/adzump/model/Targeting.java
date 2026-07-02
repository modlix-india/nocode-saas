package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Targeting implements Serializable {

    @Serial
    private static final long serialVersionUID = 3948571620394857162L;

    private Geo geo;
    private Demographics demographics;
    private Audiences audiences;
    private List<String> languages;
    private Placements placements;
    private List<KeywordSpec> keywords;
    private List<KeywordSpec> negativeKeywords;
}
