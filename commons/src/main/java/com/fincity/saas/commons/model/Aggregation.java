package com.fincity.saas.commons.model;

import java.io.Serial;
import java.io.Serializable;

import com.fincity.saas.commons.model.condition.AggregateFunction;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One measure in an {@link AggregateQuery}: a function over a field, named by an
 * alias that becomes a top-level key on every returned row.
 */
@Data
@Accessors(chain = true)
public class Aggregation implements Serializable {

    @Serial
    private static final long serialVersionUID = 7705019543310274881L;

    private AggregateFunction function;

    /**
     * The field to aggregate. Null is permitted only for COUNT, which then counts
     * rows; COUNT with a field counts rows where that field is present and not
     * null, matching SQL COUNT(col).
     */
    private String field;

    /**
     * Output key. Defaults to {@code function_field} lowercased when left unset.
     * Validated against a strict identifier charset because it lands in a $project
     * stage and in the returned map, and Mongo rejects keys carrying '.' or '$'.
     */
    private String alias;

    public String resolvedAlias() {
        if (this.alias != null && !this.alias.isBlank()) return this.alias;
        if (this.function == null) return null;
        return this.field == null || this.field.isBlank()
                ? this.function.name().toLowerCase()
                : this.function.name().toLowerCase() + "_" + this.field.replace('.', '_');
    }
}
