package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.util.DatePair;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DateStatusCount {

    private DatePair datePair;
    private List<StatusNameCount> statusCount;
}
