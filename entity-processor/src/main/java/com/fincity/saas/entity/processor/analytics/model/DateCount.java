package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;

import com.fincity.saas.entity.processor.analytics.model.base.BaseCount;
import com.fincity.saas.entity.processor.analytics.model.common.CountPercentage;
import com.fincity.saas.entity.processor.analytics.util.DatePair;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DateCount extends BaseCount<DateCount> {

	@Serial
	private static final long serialVersionUID = 6554457238568952352L;

	private DatePair datePair;

	public static DateCount of(DatePair datePair, CountPercentage totalCount) {
		return new DateCount()
				.setDatePair(datePair)
				.setTotalCount(totalCount);
	}

}
