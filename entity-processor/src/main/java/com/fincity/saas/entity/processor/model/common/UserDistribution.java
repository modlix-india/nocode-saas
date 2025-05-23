package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class UserDistribution implements Serializable {

    @Serial
    private static final long serialVersionUID = 7428944421074508272L;

    private List<ULong> profileIds;
    private List<ULong> userIds;
    private Integer percentage;
    private Integer maxLoad;
    private Integer weight;
    private Integer priority;

    private Map<DistributionType, Integer> hybridWeights;

    @JsonIgnore
    private Integer currentCount;

    @JsonIgnore
    public boolean isValidForType(DistributionType type) {
        if (type == null) return false;

        return switch (type) {
            case PERCENTAGE -> percentage != null && percentage >= 0 && percentage <= 100;
            case WEIGHTED -> weight != null && weight >= 0;
            case LOAD_BALANCED -> maxLoad != null && maxLoad > 0;
            case PRIORITY_QUEUE -> priority != null && priority >= 0;
            case HYBRID ->
                hybridWeights != null
                        && !hybridWeights.isEmpty()
                        && hybridWeights.values().stream().allMatch(w -> w >= 0);
            default -> false;
        };
    }
}
