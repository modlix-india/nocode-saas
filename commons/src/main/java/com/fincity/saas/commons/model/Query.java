package com.fincity.saas.commons.model;

import java.io.Serializable;
import java.util.List;

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

	private static final long serialVersionUID = 5943601567323412823L;

	public static final Sort DEFAULT_SORT = Sort.by(Order.desc("updatedAt"));

	private AbstractCondition condition;
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
