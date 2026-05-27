package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.ConversionActionSource;
import com.fincity.saas.entity.processor.enums.ConversionEventStatus;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * Outbox row for a conversion event waiting to be dispatched to Meta CAPI /
 * Google UploadClickConversions. One row per (ticket, mapping) — UNIQUE
 * constraint at the DB level enforces idempotency so a stage transition fired
 * twice (e.g. worker retry mid-dispatch) never causes a duplicate row.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@IgnoreGeneration
public class ConversionEvent extends BaseUpdatableDto<ConversionEvent> {

    @Serial
    private static final long serialVersionUID = 4738019462534785193L;

    private ULong ticketId;
    private ULong mappingId;
    private String eventId;
    private String eventName;
    private CampaignPlatform campaignPlatform;
    private ConversionActionSource actionSource;
    private Map<String, Object> payloadSnapshot;
    private ConversionEventStatus status = ConversionEventStatus.PENDING;
    private Map<String, Object> platformResponse;
    private String statusMessage;
    private Integer attemptCount = 0;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime sentAt;

    public ConversionEvent() {
        super();
        this.relationsMap.put(Fields.ticketId, EntitySeries.TICKET.getTable());
    }

    public ConversionEvent(ConversionEvent source) {
        super(source);
        this.ticketId = source.ticketId;
        this.mappingId = source.mappingId;
        this.eventId = source.eventId;
        this.eventName = source.eventName;
        this.campaignPlatform = source.campaignPlatform;
        this.actionSource = source.actionSource;
        this.payloadSnapshot = source.payloadSnapshot;
        this.status = source.status;
        this.platformResponse = source.platformResponse;
        this.statusMessage = source.statusMessage;
        this.attemptCount = source.attemptCount;
        this.nextAttemptAt = source.nextAttemptAt;
        this.sentAt = source.sentAt;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.CONVERSION_EVENT;
    }
}
