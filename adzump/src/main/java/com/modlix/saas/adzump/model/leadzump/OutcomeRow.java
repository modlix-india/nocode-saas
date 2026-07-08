package com.modlix.saas.adzump.model.leadzump;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.modlix.saas.adzump.model.Money;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * CRM outcomes for one ad-grain id: ticket counts and cost per milestone
 * (pipeline stage key) plus the junk rate.
 */
@Data
@Accessors(chain = true)
public class OutcomeRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1543098765412387617L;

    private AdGrainId id;
    private Map<String, Long> countByMilestone;
    private Map<String, Money> costByMilestone;
    private double junkRate;
}
