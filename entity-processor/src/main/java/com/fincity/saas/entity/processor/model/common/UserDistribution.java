package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
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

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<ULong> profileIds;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<ULong> userIds;

    private String clientCode;
    private String appCode;
    private Integer percentage;
    private Integer maxLoad;
    private Integer weight;
    private Integer priority;

    private Map<DistributionType, Integer> hybridWeights;

    @JsonIgnore
    private Integer currentCount;

    public UserDistribution(UserDistribution userDistribution) {
        this.profileIds = CloneUtil.cloneMapList(userDistribution.profileIds);
        this.userIds = CloneUtil.cloneMapList(userDistribution.userIds);
        this.clientCode = userDistribution.clientCode;
        this.appCode = userDistribution.appCode;
        this.percentage = userDistribution.percentage;
        this.maxLoad = userDistribution.maxLoad;
        this.weight = userDistribution.weight;
        this.priority = userDistribution.priority;
        this.hybridWeights = CloneUtil.cloneMapObject(userDistribution.hybridWeights);
        this.currentCount = userDistribution.currentCount;
    }

    @JsonIgnore
    public boolean isValidForType(DistributionType type) {
        if (type == null) return false;

        return switch (type) {
            case ROUND_ROBIN -> true;
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

    @JsonIgnore
    public List<BigInteger> getProfileIdsInt() {
        return profileIds.stream().map(ULong::toBigInteger).toList();
    }

    public UserDistribution transformToValid() {
        this.setProfileIds(this.profileIds.stream().distinct().toList());
        this.setUserIds(this.userIds.stream().distinct().toList());
        return this;
    }
}
