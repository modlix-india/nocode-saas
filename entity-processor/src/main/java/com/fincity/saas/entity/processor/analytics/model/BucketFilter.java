package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@AllArgsConstructor
@FieldNameConstants
public class BucketFilter implements Serializable {

    private List<ULong> userIds;
    private List<String> sources;
    private List<String> subSources;
    private List<ULong> productIds;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
