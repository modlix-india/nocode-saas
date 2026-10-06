package com.fincity.saas.commons.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fincity.saas.commons.model.condition.AbstractCondition;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A question about a storage's CHILDREN, answered alongside the parent row.
 *
 * Joins only work in one direction, because a relation is declared on the side that
 * holds the id: an order knows its customer, a customer knows nothing of its orders.
 * So "customers with more than three orders", "customers whose last order was over a
 * year ago" and "orders per customer" are not slow today, they are inexpressible.
 *
 * This is the other direction, and it is a semi-join rather than a join: the children
 * are grouped first, so exactly one row comes back per parent. That matters for more
 * than tidiness. A join to the many side multiplies parent rows, which makes LIMIT
 * and OFFSET count duplicates and turns the page size into a lie.
 *
 * It is also how a to-many relation becomes answerable at all, without the JSON
 * column scan that joining through one would need.
 */
@Data
@Accessors(chain = true)
public class StorageSubQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 4406216441128553461L;

    /** The child storage, for example {@code orders}. */
    private String storage;

    /**
     * The relation ON THE CHILD that points back here, for example {@code customer}.
     *
     * Named rather than inferred. Two relations from the same child storage to the
     * same parent are perfectly ordinary - an order's buyer and its payer - and
     * guessing between them would silently answer a different question.
     */
    private String relation;

    /** How the measures are addressed, as {@code alias.measure}. Defaults to the storage name. */
    private String alias;

    /** Filters the CHILDREN before they are counted. */
    private AbstractCondition condition;

    /**
     * What to measure. Defaults to a plain row count under the alias {@code count}.
     */
    private List<Aggregation> aggregations;

    /** Filters the GROUPS, over the measure aliases: {@code count > 3}. */
    private AbstractCondition having;

    /**
     * Whether a parent with no matching children survives.
     *
     * True is a filter - "customers who have ordered". False keeps everyone and
     * leaves the measures null, which is what you want for "orders per customer,
     * including the ones who never did".
     */
    private Boolean required = Boolean.TRUE;

    public String resolvedAlias() {
        if (this.alias != null && !this.alias.isBlank()) return this.alias;
        return this.storage;
    }
}
