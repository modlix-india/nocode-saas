package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.util.FilterUtil;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
public class BucketFilter implements Serializable {

    private List<ULong> createdByIds;
    private List<ULong> assignedUserIds;
    private List<ULong> clientIds;
    private List<String> sources;
    private List<String> subSources;
    private List<ULong> stageIds;
    private List<ULong> statusIds;
    private List<ULong> productIds;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean includeZero;
    private boolean includeAll;

    public BucketFilter filterCreatedByIds(List<ULong> createdByIds) {
        this.createdByIds = FilterUtil.intersectLists(this.createdByIds, createdByIds);
        return this;
    }

    public BucketFilter filterAssignedUserIds(List<ULong> assignedUserIds) {
        this.assignedUserIds = FilterUtil.intersectLists(this.assignedUserIds, assignedUserIds);
        return this;
    }

    public BucketFilter filterClientIds(List<ULong> clientIds) {
        this.clientIds = FilterUtil.intersectLists(this.clientIds, clientIds);
        return this;
    }

    public BucketFilter filterSources(List<String> sources) {
        this.sources = FilterUtil.intersectLists(this.sources, sources);
        return this;
    }

    public BucketFilter filterSubSources(List<String> subSources) {
        this.subSources = FilterUtil.intersectLists(this.subSources, subSources);
        return this;
    }

    public BucketFilter filterStageIds(List<ULong> stageIds) {
        this.stageIds = FilterUtil.intersectLists(this.stageIds, stageIds);
        return this;
    }

    public BucketFilter filterStatusIds(List<ULong> statusIds) {
        this.statusIds = FilterUtil.intersectLists(this.statusIds, statusIds);
        return this;
    }

    public BucketFilter filterProductIds(List<ULong> productIds) {
        this.productIds = FilterUtil.intersectLists(this.productIds, productIds);
        return this;
    }

    public ULong[] getCreatedByIdsArray() {
        return this.createdByIds == null ? new ULong[0] : this.createdByIds.toArray(new ULong[0]);
    }

    public ULong[] getAssignedUserIdsArray() {
        return this.assignedUserIds == null ? new ULong[0] : this.assignedUserIds.toArray(new ULong[0]);
    }

    public ULong[] getClientIdsArray() {
        return this.clientIds == null ? new ULong[0] : this.clientIds.toArray(new ULong[0]);
    }

    public String[] getSourcesArray() {
        return this.sources == null ? new String[0] : this.sources.toArray(new String[0]);
    }

    public String[] getSubSourcesArray() {
        return this.subSources == null ? new String[0] : this.subSources.toArray(new String[0]);
    }

    public ULong[] getStageIdsArray() {
        return this.stageIds == null ? new ULong[0] : this.stageIds.toArray(new ULong[0]);
    }

    public ULong[] getStatusIdsArray() {
        return this.statusIds == null ? new ULong[0] : this.statusIds.toArray(new ULong[0]);
    }

    public ULong[] getProductIdsArray() {
        return this.productIds == null ? new ULong[0] : this.productIds.toArray(new ULong[0]);
    }
}
