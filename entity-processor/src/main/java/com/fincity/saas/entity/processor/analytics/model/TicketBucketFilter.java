package com.fincity.saas.entity.processor.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.util.FilterUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class TicketBucketFilter extends BaseFilter<TicketBucketFilter> implements Serializable {

    @Serial
    private static final long serialVersionUID = 2989665207529993037L;

    private List<String> sources;
    private List<String> subSources;
    private List<ULong> stageIds;
    private List<ULong> statusIds;
    private List<ULong> productIds;
    private boolean includeAll;

    @JsonIgnore
    private FieldData fieldData = new FieldData();

    public TicketBucketFilter filterSources(List<String> sources) {
        this.sources = FilterUtil.intersectLists(this.sources, sources);
        return this;
    }

    public TicketBucketFilter filterSubSources(List<String> subSources) {
        this.subSources = FilterUtil.intersectLists(this.subSources, subSources);
        return this;
    }

    public TicketBucketFilter filterStageIds(List<ULong> stageIds) {
        this.stageIds = FilterUtil.intersectLists(this.stageIds, stageIds);
        return this;
    }

    public TicketBucketFilter filterStatusIds(List<ULong> statusIds) {
        this.statusIds = FilterUtil.intersectLists(this.statusIds, statusIds);
        return this;
    }

    public TicketBucketFilter filterProductIds(List<ULong> productIds) {
        this.productIds = FilterUtil.intersectLists(this.productIds, productIds);
        return this;
    }

    public TicketBucketFilter setStages(List<IdAndValue<ULong, String>> stages) {
        this.fieldData.setStages(stages);
        return this;
    }

    public TicketBucketFilter setStatuses(List<IdAndValue<ULong, String>> statuses) {
        this.fieldData.setStatuses(statuses);
        return this;
    }

    public TicketBucketFilter setProducts(List<IdAndValue<ULong, String>> products) {
        this.fieldData.setProducts(products);
        return this;
    }

    @Data
    @Accessors(chain = true)
    @ToString(callSuper = true)
    public static class FieldData implements Serializable {

        @Serial
        private static final long serialVersionUID = 3152573318393249502L;

        private List<IdAndValue<ULong, String>> stages;
        private List<IdAndValue<ULong, String>> statuses;
        private List<IdAndValue<ULong, String>> products;
    }
}
