package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.model.DatePair;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DateStatusCount implements Serializable {

    @Serial
    private static final long serialVersionUID = 8085978945156919102L;

    private DatePair datePair;
    private List<StatusNameCount> statusCount;
}
