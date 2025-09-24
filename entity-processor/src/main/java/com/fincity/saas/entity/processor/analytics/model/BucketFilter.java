package com.fincity.saas.entity.processor.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
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

    @JsonIgnore
    private FieldData fieldData;

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

    public BucketFilter setCreatedBys(List<IdAndValue<ULong, String>> createdBys) {
        this.fieldData = this.fieldData == null ? new FieldData() : this.fieldData;
        this.fieldData.setCreatedBys(createdBys);
        return this;
    }

    public BucketFilter setAssignedUsers(List<IdAndValue<ULong, String>> assignedUsers) {
        this.fieldData = this.fieldData == null ? new FieldData() : this.fieldData;
        this.fieldData.setAssignedUsers(assignedUsers);
        return this;
    }

    public BucketFilter setClients(List<IdAndValue<ULong, String>> clients) {
        this.fieldData = this.fieldData == null ? new FieldData() : this.fieldData;
        this.fieldData.setClients(clients);
        return this;
    }

    public BucketFilter setStages(List<IdAndValue<ULong, String>> stages) {
        this.fieldData = this.fieldData == null ? new FieldData() : this.fieldData;
        this.fieldData.setStages(stages);
        return this;
    }

    public BucketFilter setStatuses(List<IdAndValue<ULong, String>> statuses) {
        this.fieldData = this.fieldData == null ? new FieldData() : this.fieldData;
        this.fieldData.setStatuses(statuses);
        return this;
    }

    public BucketFilter setProducts(List<IdAndValue<ULong, String>> products) {
        this.fieldData = this.fieldData == null ? new FieldData() : this.fieldData;
        this.fieldData.setProducts(products);
        return this;
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class FieldData {

        private List<IdAndValue<ULong, String>> createdBys;

        private List<IdAndValue<ULong, String>> assignedUsers;

        private List<IdAndValue<ULong, String>> clients;

        private List<IdAndValue<ULong, String>> stages;

        private List<IdAndValue<ULong, String>> statuses;

        private List<IdAndValue<ULong, String>> products;
    }
}
