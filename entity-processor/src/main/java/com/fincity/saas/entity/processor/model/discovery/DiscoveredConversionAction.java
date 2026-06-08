package com.fincity.saas.entity.processor.model.discovery;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Read-only view of a single Google Ads conversion action, as returned by the
 * Google Ads API for the conversion-mapping picker. The {@code resourceName} is
 * exactly what goes into a mapping's {@code platformActionId}
 * ({@code customers/{customerId}/conversionActions/{id}}).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DiscoveredConversionAction implements Serializable {

    @Serial
    private static final long serialVersionUID = 5821037462910058471L;

    private String resourceName;
    private String id;
    private String name;
    private String status;
    private String type;
    private String category;
    private String countingType;
    private Integer clickThroughLookbackWindowDays;
    private Boolean primaryForGoal;
}
