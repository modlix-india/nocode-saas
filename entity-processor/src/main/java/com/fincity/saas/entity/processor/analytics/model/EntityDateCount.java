package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.analytics.model.common.CountPercentage;
import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EntityDateCount implements Serializable {

    @Serial
    private static final long serialVersionUID = 8009993639629612555L;

    private ULong id;

    private String name;

    private CountPercentage totalCount;

    private List<DateCount> dateCounts;

    public EntityDateCount(ULong id, String name, List<DateCount> dateCounts, boolean includePercentage) {
        this.id = id;
        this.name = name;

        this.dateCounts = dateCounts.stream()
                .sorted(Comparator.comparing(DateCount::getDatePair))
                .toList();

        long totalCountValue = this.dateCounts.stream()
                .mapToLong(dateCount -> dateCount.getCount() != null
                                && dateCount.getCount().getCount() != null
                        ? dateCount.getCount().getCount().longValue()
                        : 0L)
                .sum();

        this.totalCount = includePercentage
                ? CountPercentage.of(totalCountValue, 0.0)
                : CountPercentage.withCount(totalCountValue);
    }
}
