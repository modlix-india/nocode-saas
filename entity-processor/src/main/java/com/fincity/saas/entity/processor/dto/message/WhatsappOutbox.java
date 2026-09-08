package com.fincity.saas.entity.processor.dto.message;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.message.WhatsappOutboxStatus;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * One automated message waiting for the pacing gate to let it through.
 *
 * <p>Layer 2 of two. The bridge's own queue spaces sends five to fifteen seconds apart and cannot
 * hold anything for a day; a fifteen-minute sweeper cannot produce a seven-second gap. Hence two
 * mechanisms, in two services, at two timescales.
 *
 * <p>Unlike the dispatch outbox next door, <b>rows here are not deleted on success</b>. This is the
 * record of what was sent, to whom, and which rule allowed it. If a customer's number is ever
 * banned it is the only account of what actually happened, and short of that it is the only way to
 * tell whether the caps are set anywhere near right.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@IgnoreGeneration
public class WhatsappOutbox extends BaseUpdatableDto<WhatsappOutbox> {

    @Serial
    private static final long serialVersionUID = 4471553288845163929L;

    private ULong ticketId;
    private ULong productId;
    private ULong stageId;

    /** The rule this came from, so a welcome packet can be traced back to what queued it. */
    private ULong configId;

    /**
     * The session this will send from, resolved when queued rather than when sent.
     *
     * <p>Resolved early so the caps are computed against the number that will actually do the
     * sending. Deciding at send time would mean a message queued under one number's warm-up
     * allowance going out under another's.
     */
    private String bridgeSessionId;

    private String toPhone;

    /** Already has its variant chosen and its variables substituted. */
    private String bodyText;

    private FileDetail assetFileDetail;
    private String caption;

    private WhatsappOutboxStatus status = WhatsappOutboxStatus.PENDING;

    /**
     * Which gate refused to release this on the last sweep.
     *
     * <p>A free string rather than an enum, deliberately. The set of reasons grows every time a cap
     * is tuned, and needing a schema change and a redeploy to record a new one is how rows end up
     * held with no reason at all, which is the thing that makes this unexplainable three months
     * later.
     */
    private String holdReason;

    /** Position in a packet. A packet drains in order and stops entirely if one message fails. */
    private Integer sequenceOrder = 0;

    /** Not before this. Set when a gate reschedules rather than refuses, e.g. quiet hours. */
    private LocalDateTime earliestSendAt;

    private LocalDateTime sentAt;

    /** WhatsApp's id for the sent message, linking this row to the conversation thread. */
    private String messageId;

    private Integer attempts = 0;
    private String lastError;

    /** RELEASED_BY_REPLY, RELEASED_BY_TIMER or FORCED. */
    private String sendDecision;

    private ULong forcedBy;

    public WhatsappOutbox() {
        super();
    }

    public WhatsappOutbox(WhatsappOutbox other) {
        super(other);
        this.ticketId = other.ticketId;
        this.productId = other.productId;
        this.stageId = other.stageId;
        this.configId = other.configId;
        this.bridgeSessionId = other.bridgeSessionId;
        this.toPhone = other.toPhone;
        this.bodyText = other.bodyText;
        this.assetFileDetail = other.assetFileDetail;
        this.caption = other.caption;
        this.status = other.status;
        this.holdReason = other.holdReason;
        this.sequenceOrder = other.sequenceOrder;
        this.earliestSendAt = other.earliestSendAt;
        this.sentAt = other.sentAt;
        this.messageId = other.messageId;
        this.attempts = other.attempts;
        this.lastError = other.lastError;
        this.sendDecision = other.sendDecision;
        this.forcedBy = other.forcedBy;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.WHATSAPP_OUTBOX;
    }
}
