package com.fincity.saas.commons.model;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One relation pulled into a query as a join.
 *
 * Different from the eager fetch the platform already has. Eager expands a relation
 * into its objects after the page has been read, with one extra query per field; it
 * cannot filter the parent by a child's field, sort by one, or aggregate across the
 * pair. Those three are the reason joins exist at all.
 */
@Data
@Accessors(chain = true)
public class StorageJoin implements Serializable {

    @Serial
    private static final long serialVersionUID = 6377231044188893013L;

    /** A key in the parent storage's {@code relations} map. */
    private String relation;

    /**
     * How joined fields are addressed, as {@code alias.field}.
     *
     * Defaults to the relation name. It has to be given explicitly for a self-join,
     * where the two sides are the same table and the name alone cannot say which is
     * meant - {@code monkbars.tportTree.parent} points at {@code tportTree}.
     */
    private String alias;

    private JoinType type = JoinType.LEFT;

    public String resolvedAlias() {
        if (this.alias != null && !this.alias.isBlank()) return this.alias;
        return this.relation;
    }
}
