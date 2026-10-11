package com.fincity.saas.commons.model.condition;

public enum AggregateFunction {

    MAX,
    MIN,
    SUM,
    AVG,
    COUNT,

    /**
     * COUNT(DISTINCT field): the number of different non-null values in a group.
     * Needs a field. "How many deals reached this stage" when a deal can reach it
     * twice is the case it exists for: COUNT would count the deal twice.
     */
    COUNT_DISTINCT
}

