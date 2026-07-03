package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BudgetPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = -7301928465731209846L;

    private String currency;
    private Money dailyBudget;
    private Money totalBudget;
    private List<BudgetSplit> split;
}
