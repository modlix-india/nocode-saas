package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.util.Case;
import com.fincity.saas.commons.util.CloneUtil;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class UserDistribution implements Serializable {

    @Serial
    private static final long serialVersionUID = 7428944421074508272L;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<ULong> profileIds = List.of();

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<ULong> userIds = List.of();

    private String clientCode;
    private String appCode;
    private Integer percentage;
    private Integer maxLoad;
    private Integer weight;
    private Integer priority;

    private Map<DistributionType, Integer> hybridWeights;

    @JsonIgnore
    private Integer currentCount;

    public UserDistribution() {}

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

    public static Schema getSchema() {
        return Schema.ofObject(Case.PASCAL.getConverter().apply(UserDistribution.class.getSimpleName()))
                .setProperties(Map.of(
                        Fields.profileIds,
                        Schema.ofArray(
                                Fields.profileIds,
                                Schema.ofLong(Fields.profileIds).setMinimum(1)),
                        Fields.userIds,
                        Schema.ofArray(
                                Fields.userIds, Schema.ofLong(Fields.userIds).setMinimum(1)),
                        Fields.percentage,
                        Schema.ofInteger(Fields.percentage).setMinimum(0).setMaximum(100),
                        Fields.weight,
                        Schema.ofInteger(Fields.weight).setMinimum(0),
                        Fields.maxLoad,
                        Schema.ofInteger(Fields.maxLoad).setMinimum(1),
                        Fields.priority,
                        Schema.ofInteger(Fields.priority).setMinimum(0),
                        Fields.hybridWeights,
                        Schema.ofObject(Fields.hybridWeights)
                                .setName(Fields.hybridWeights)
                                .setProperties(Arrays.stream(DistributionType.values())
                                        .collect(Collectors.toMap(
                                                Enum::name,
                                                type -> Schema.ofInteger(type.name())
                                                        .setMinimum(0),
                                                (a, b) -> b,
                                                () -> HashMap.newHashMap(DistributionType.values().length)))),
                        Fields.currentCount,
                        Schema.ofInteger(Fields.currentCount).setMinimum(0)));
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

    @JsonIgnore
    public UserDistribution transformToValid() {
        this.setProfileIds(this.profileIds.stream().distinct().toList());
        this.setUserIds(this.userIds.stream().distinct().toList());
        return this;
    }
}
