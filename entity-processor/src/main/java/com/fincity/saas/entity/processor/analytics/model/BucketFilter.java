package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@FieldNameConstants
public class BucketFilter implements Serializable {

    private List<ULong> userIds;
    private List<String> sources;
    private List<String> subSources;
    private List<ULong> stageIds;
    private List<ULong> statusIds;
    private List<ULong> productIds;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private static <T> List<T> intersectLists(List<T> current, List<T> incoming) {
        if (incoming == null || incoming.isEmpty()) return current;

        if (current == null || current.isEmpty()) return incoming;

        return incoming.stream().filter(current::contains).toList();
    }

    public BucketFilter filterUserIds(List<ULong> userIds) {
        this.userIds = intersectLists(this.userIds, userIds);
        return this;
    }

    public BucketFilter filterSources(List<String> sources) {
        this.sources = intersectLists(this.sources, sources);
        return this;
    }

    public BucketFilter filterSubSources(List<String> subSources) {
        this.subSources = intersectLists(this.subSources, subSources);
        return this;
    }

    public BucketFilter filterStageIds(List<ULong> stageIds) {
        this.stageIds = intersectLists(this.stageIds, stageIds);
        return this;
    }

    public BucketFilter filterStatusIds(List<ULong> statusIds) {
        this.statusIds = intersectLists(this.statusIds, statusIds);
        return this;
    }

    public BucketFilter filterProductIds(List<ULong> productIds) {
        this.productIds = intersectLists(this.productIds, productIds);
        return this;
    }
}
