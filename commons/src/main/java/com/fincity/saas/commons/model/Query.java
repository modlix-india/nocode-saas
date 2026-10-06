package com.fincity.saas.commons.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

import com.fincity.saas.commons.model.condition.AbstractCondition;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Query implements Serializable {

    @Serial
    private static final long serialVersionUID = 5943601567323412823L;

    public static final Sort DEFAULT_SORT = Sort.by(Order.desc("updatedAt"));

    private AbstractCondition condition;

    /**
     * Relations pulled in as joins, so the condition and the sort can reach across
     * them.
     *
     * Separate from {@code eager}, which expands a relation into its objects after
     * the fact and cannot be filtered or sorted on. A backend that cannot join says
     * so rather than ignoring this.
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
    private Map<String, AbstractCondition> subQueryConditions;
    private int size = 10;
    private int page = 0;
    private Sort sort = DEFAULT_SORT;
    private Boolean count = Boolean.TRUE;
    private List<String> fields;
    private Boolean excludeFields = Boolean.FALSE;
    private Boolean eager = Boolean.FALSE;
    private List<String> eagerFields;

    public Pageable getPageable() {
        return PageRequest.of(this.page, this.size, this.sort);
    }
}
