package com.fincity.saas.core.model;

import java.util.List;

import com.fincity.saas.commons.model.Query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DataServiceQuery extends Query {

	private static final long serialVersionUID = 8632577497769933536L;

	private List<String> fields;
	private Boolean excludeFields = Boolean.FALSE;
}
