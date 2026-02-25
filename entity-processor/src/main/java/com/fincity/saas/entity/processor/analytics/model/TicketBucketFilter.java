package com.fincity.saas.entity.processor.analytics.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import com.fincity.saas.entity.processor.util.CollectionUtil;
import com.fincity.saas.entity.processor.util.DatePair;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
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
public class TicketBucketFilter extends BaseFilter<TicketBucketFilter> {

    @Serial
    private static final long serialVersionUID = 2989665207529993037L;

    private List<String> sources;
    private List<String> subSources;
    private List<ULong> stageIds;
    private List<ULong> statusIds;
    private List<ULong> productIds;
    private boolean includeAll;
    private boolean includeNone;

    @JsonIgnore
    private FieldData fieldData = new FieldData();

    public TicketBucketFilter filterSources(List<String> sources) {
        this.sources = CollectionUtil.intersectLists(this.sources, sources);
        return this;
    }

    public TicketBucketFilter filterSubSources(List<String> subSources) {
        this.subSources = CollectionUtil.intersectLists(this.subSources, subSources);
        return this;
    }

    public TicketBucketFilter filterStageIds(List<ULong> stageIds) {
        this.stageIds = CollectionUtil.intersectLists(this.stageIds, stageIds);
        return this;
    }

    public TicketBucketFilter filterStatusIds(List<ULong> statusIds) {
        this.statusIds = CollectionUtil.intersectLists(this.statusIds, statusIds);
        return this;
    }

    public TicketBucketFilter filterProductIds(List<ULong> productIds) {
        this.productIds = CollectionUtil.intersectLists(this.productIds, productIds);
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

    @Override
    public BaseFilter.ReportOptions toReportOptions() {

        LocalDateTime startInTimezone = DatePair.convertUtcToTimezone(super.getStartDate(), super.getTimezone());
        LocalDateTime endInTimezone = DatePair.convertUtcToTimezone(super.getEndDate(), super.getTimezone());

        return new BaseFilter.ReportOptions(
                DatePair.of(startInTimezone, endInTimezone, super.getTimezone()),
                this.getTimePeriod(),
                this.isIncludeZero(),
                this.isIncludePercentage(),
                this.isIncludeTotal(),
                this.isIncludeAllTotal(),
                this.includeNone,
                this.getTimezone());
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
