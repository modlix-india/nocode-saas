package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The result of an OutcomeQuery: one row per requested ad-grain id.
 */
@Data
@Accessors(chain = true)
public class CrmOutcomes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387618L;

    private Grain grain;
    private List<OutcomeRow> rows;
}
