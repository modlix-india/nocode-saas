package com.fincity.saas.commons.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.model.condition.AbstractCondition;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A grouped read over a storage: filter, group, measure, filter again, page.
 *
 * Deliberately shaped like {@link Query} rather than extending it. The two share
 * only the condition and the paging, and every other field on Query (fields,
 * excludeFields, eager, eagerFields, subQueryConditions) is meaningless once rows
 * have been collapsed into groups.
 *
 * Results come back FLAT: one row per group with the group keys and the measures
 * side by side at the top level, never a nested _id. That is what lets a chart
 * bind the result directly instead of reshaping it first.
 */
@Data
@Accessors(chain = true)
public class AggregateQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1150443829965538761L;

    /**
     * Upper bound on groups returned in one page, enforced server side.
     *
     * A group-by on an unbounded field can produce arbitrarily many groups, and
     * unlike a row read the caller has no way to know that in advance.
     */
    public static final int MAX_SIZE = 1000;

    /** Becomes the $match stage, reusing the ordinary filter vocabulary. */
    private AbstractCondition condition;

    /**
     * Relations pulled in as joins, so a group key or a measure can come from the
     * other side.
     *
     * This is the only part of the query model Mongo cannot answer at all: an
     * aggregation pipeline sees one collection. A backend that cannot join refuses
     * rather than returning a smaller truth.
     */
    private List<StorageJoin> joins;

    /**
     * Questions about the children, answered per parent row.
     *
     * The other direction from {@code joins}: a relation is declared on the side
     * that holds the id, so a join can only ever walk towards the parent. See
     * {@link StorageSubQuery}.
     */
    private List<StorageSubQuery> subQueries;

    /** Empty or null collapses the whole collection into a single row. */
    private List<GroupByField> groupBy;

    private List<Aggregation> aggregations;

    /**
     * Filter applied AFTER grouping, against the measure and group-key aliases.
     *
     * Typed as a plain AbstractCondition on purpose: post-group the aliases are
     * ordinary field names, so FilterCondition and ComplexCondition work unchanged.
     * HavingCondition is a JOOQ-shaped thing carrying its aggregate inline and is
     * rejected here rather than misread.
     */
    private AbstractCondition having;

    /** Sorts on group-key or measure aliases only; anything else is rejected. */
    private Sort sort;

    private int size = 100;
    private int page = 0;

    /** Total group count, which costs a second $facet branch. Off by default. */
    private Boolean count = Boolean.FALSE;

    public Pageable getPageable() {
        int effective = Math.min(this.size < 1 ? 1 : this.size, MAX_SIZE);
        return this.sort == null || this.sort.isUnsorted()
                ? PageRequest.of(this.page, effective)
                : PageRequest.of(this.page, effective, this.sort);
    }
}
