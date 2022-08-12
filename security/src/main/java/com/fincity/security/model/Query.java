package com.fincity.security.model;

import java.io.Serializable;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

import com.fincity.security.model.condition.AbstractCondition;

import lombok.Data;

@Data
public class Query implements Serializable {

	private static final long serialVersionUID = -3002837412473164388L;

	private AbstractCondition condition;
	private int size = 10;
	private int page = 0;
	private Sort sort = Sort.by(Order.desc("updatedAt"));
}
