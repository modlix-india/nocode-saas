package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

/**
 * One node in the stage column tree returned alongside campaign report rows.
 * Mirrors {@code entity_processor_stages} hierarchy for the product's template.
 * Top-level nodes are parent stages (PARENT_LEVEL_0 NULL); their {@code children}
 * are the substages.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class StageNode implements Serializable {

    @Serial
    private static final long serialVersionUID = -4561293841726610991L;

    private ULong id;
    private String name;
    /** LEAD / MQL / SQL / WON / LOST / CUSTOM, or null if untagged. */
    private String funnelStage;
    private int order;
    private List<StageNode> children;
}
