package com.fincity.saas.entity.processor.analytics.model;

import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Request body for {@code POST /api/entity/processor/analytics/campaigns/tree}.
 *
 * <p>Date range is supplied in the caller's local timezone via {@code timezone}
 * (IANA, e.g. {@code "Asia/Kolkata"}); the server converts to UTC for the
 * underlying queries. Today only {@link Depth#CAMPAIGN} is supported — adset
 * and ad expansion are pending phase-7 E5.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CampaignTreeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Depth {
        CAMPAIGN,
        ADSET,
        AD
    }

    private Identity productId;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String timezone;

    /** Optional platform filter ({@code FACEBOOK}, {@code GOOGLE}, ...). */
    private List<String> platforms;

    private Depth depth = Depth.CAMPAIGN;
}
