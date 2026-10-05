package com.fincity.saas.commons.model;

/**
 * Whether a parent row survives a join that matches nothing.
 */
public enum JoinType {

    /**
     * Keep the parent either way, which is what the eager fetch already does: a row
     * whose relation is null still comes back, with nothing under the alias.
     */
    LEFT,

    /** Drop the parent when the relation matches nothing, so the join is a filter. */
    INNER
}
