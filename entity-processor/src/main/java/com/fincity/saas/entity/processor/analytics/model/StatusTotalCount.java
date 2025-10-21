package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.base.BaseStatusCount;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StatusTotalCount extends BaseStatusCount<StatusTotalCount> {

	@Override
	public String getName() {
		return "Total";
	}
}
