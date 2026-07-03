package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.enums.MatchType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KeywordSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = -6102938475610293847L;

    private String text;
    private MatchType matchType;
}
