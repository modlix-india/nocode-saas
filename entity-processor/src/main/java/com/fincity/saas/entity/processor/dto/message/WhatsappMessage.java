package com.fincity.saas.entity.processor.dto.message;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageStatus;
import com.fincity.saas.entity.processor.enums.message.WhatsappMessageType;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * A WhatsApp message, stored here rather than in the message service.
 *
 * <p>It lives here because search needs a join: filtering message content while restricting to the
 * deals a user can see, ordered and paginated, only works when the content and the access condition
 * are in one query. The message service keeps the provider side (webhook, Graph API, templates,
 * WABAs, numbers) and hands each inbound message to whichever service owns the receiving number.
 *
 * <p>The Meta payloads stay {@link Map}s. This service stores and returns them but never constructs
 * or interprets one, so typing them would mean duplicating the provider model package.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappMessage extends BaseUpdatableDto<WhatsappMessage> {

    /** Meta's message id, and the idempotency key every write path upserts on. */
    private String messageId;

    private ULong whatsappBusinessAccountId;
    private ULong whatsappPhoneNumberId;

    /**
     * The business number as dialled, captured at write time. Kept alongside the id because a
     * number can later be pointed at a different product, and history must keep the number the
     * conversation actually happened on.
     */
    private String whatsappPhoneNumber;

    /**
     * The linked session that carried this message.
     *
     * <p>The column arrived with the bridge pivot and stayed unmapped, so nothing wrote it. That was
     * not merely untidy: {@code WhatsappMessageDAO.sessionWindow} filters on it, so the query behind
     * a number's recent-failure count matched nothing and always answered zero, and the pacing that
     * is supposed to back a number off when it starts being rejected never saw a reason to.
     *
     * <p>Kept alongside {@link #whatsappPhoneNumber} rather than derived from it: a number can be
     * unlinked and relinked under a new session, and pacing is a question about the session.
     */
    private String bridgeSessionId;

    /**
     * The deal this message was filed against.
     *
     * <p>A label, not the access boundary. Reads resolve the set of deals the caller can see first
     * and match against this, which is why a message mis-filed onto a sibling deal of the same
     * customer degrades to a wrong label rather than a lost message.
     */
    private ULong ticketId;

    private Integer fromDialCode;
    private String from;
    private Integer toDialCode;
    private String to;

    private String customerWaId;
    private Integer customerDialCode;
    private String customerPhoneNumber;

    private WhatsappMessageType messageType = WhatsappMessageType.TEXT;
    private WhatsappMessageStatus messageStatus = WhatsappMessageStatus.SENT;

    private LocalDateTime sentTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime readTime;
    private LocalDateTime failedTime;
    private String failureReason;

    /**
     * Primitive on purpose, matching the message service's DTO exactly.
     *
     * <p>Lombok gives a primitive {@code boolean isOutbound} the getter {@code isOutbound()}, which
     * Jackson publishes as {@code "outbound"}. A {@code Boolean} would get {@code getIsOutbound()}
     * and publish as {@code "isOutbound"} instead. The UI binds {@code Parent.outbound} in dozens of
     * places to decide which side of the thread a bubble sits on, so the wrapper type would have
     * silently flipped every message to inbound rather than failing visibly.
     */
    private boolean isOutbound = true;

    /** Plain text extracted at write time, so search can use an index instead of scanning JSON. */
    private String bodyText;

    private Map<String, Object> message;
    private FileDetail mediaFileDetail;
    private Map<String, Object> inMessage;
    private Map<String, Object> messageResponse;

    public WhatsappMessage() {
        super();
    }

    public WhatsappMessage(WhatsappMessage other) {
        super(other);
        this.messageId = other.messageId;
        this.whatsappBusinessAccountId = other.whatsappBusinessAccountId;
        this.whatsappPhoneNumberId = other.whatsappPhoneNumberId;
        this.whatsappPhoneNumber = other.whatsappPhoneNumber;
        this.ticketId = other.ticketId;
        this.fromDialCode = other.fromDialCode;
        this.from = other.from;
        this.toDialCode = other.toDialCode;
        this.to = other.to;
        this.customerWaId = other.customerWaId;
        this.customerDialCode = other.customerDialCode;
        this.customerPhoneNumber = other.customerPhoneNumber;
        this.messageType = other.messageType;
        this.messageStatus = other.messageStatus;
        this.sentTime = other.sentTime;
        this.deliveredTime = other.deliveredTime;
        this.readTime = other.readTime;
        this.failedTime = other.failedTime;
        this.failureReason = other.failureReason;
        this.isOutbound = other.isOutbound;
        this.bodyText = other.bodyText;
        this.message = other.message;
        this.mediaFileDetail = other.mediaFileDetail;
        this.inMessage = other.inMessage;
        this.messageResponse = other.messageResponse;
    }
}
