package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EntityStatusCount implements Serializable {

    @Serial
    private static final long serialVersionUID = 8009993639629612554L;

    private ULong id;

    private String name;

    private CountPercentage totalCount;

    private List<StatusEntityCount> statusCount;

    public EntityStatusCount(ULong id, String name, List<StatusEntityCount> statusCount, boolean includePercentage) {
        this.id = id;
        this.name = name;
        this.statusCount = statusCount;

        long totalCountValue = statusCount.stream()
                .mapToLong(statusEntityCount -> statusEntityCount.getTotalCount() != null
                                && statusEntityCount.getTotalCount().getCount() != null
                        ? statusEntityCount.getTotalCount().getCount().longValue()
                        : 0L)
                .sum();

        this.totalCount = includePercentage
                ? CountPercentage.of(totalCountValue, 0.0)
                : CountPercentage.withCount(totalCountValue);
    }
}
