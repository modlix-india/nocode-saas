package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BudgetSplit implements Serializable {

    @Serial
    private static final long serialVersionUID = 2384756192837465019L;

    private Platform platform;
    private Double percent;
}
