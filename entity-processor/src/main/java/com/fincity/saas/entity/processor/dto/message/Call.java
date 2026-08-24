package com.fincity.saas.entity.processor.dto.message;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.oserver.message.enums.call.CallStatus;
import com.fincity.saas.entity.processor.oserver.message.enums.call.ExotelCallStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

/**
 * A call, stored here rather than in the message service.
 *
 * <p>Same reason as {@link WhatsappMessage}: only this service can answer whether a user may see a
 * given call, because only this service knows what a deal is. The message service keeps the provider
 * relationship (placing the call, the connect applet, status callbacks, connections and tokens) and
 * hands each call event to whichever service owns the number it happened on.
 *
 * <p>Field names deliberately mirror the message service's {@code ExotelCall}, because the deal
 * profile binds them directly and the move is meant to change who is allowed to read a call, not
 * what a call looks like. The provider payloads stay {@link Map}s: this service stores and returns
 * them but never constructs or interprets one, and a Map preserves the provider's own key casing
 * that the page reads through.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Call extends BaseUpdatableDto<Call> {

    /**
     * The provider's call id (Exotel calls it the Sid), and the idempotency key for the handoff.
     *
     * <p>Every write path upserts on it, which is what makes a duplicate callback, an outbox replay
     * after a failed delete, and a status update arriving before the call row all safe. Null for a
     * call logged by hand that never touched a provider.
     */
    private String providerCallId;

    private String parentCallSid;
    private String accountSid;

    /**
     * The deal this call was filed against.
     *
     * <p>A label, not the access boundary. Reads resolve the visible deal set first and match
     * against this, so a call mis-filed onto a sibling deal of the same customer degrades to a wrong
     * label rather than a leak.
     */
    private ULong ticketId;

    private ULong productId;

    private String connectionName;
    private String callProvider;

    /**
     * Primitive on purpose, exactly as on {@link WhatsappMessage} and for the same reason. Lombok
     * gives {@code boolean isOutbound} the getter {@code isOutbound()}, which Jackson publishes as
     * {@code "outbound"}; a {@code Boolean} would publish {@code "isOutbound"} instead and quietly
     * flip every call's direction in the UI rather than failing visibly.
     */
    private boolean isOutbound = true;

    private Integer fromDialCode;
    private String from;
    private Integer toDialCode;
    private String to;

    /** The customer end of the call, resolved from direction once at write time. */
    private Integer customerDialCode;

    private String customerPhoneNumber;

    private String callerId;

    /** The normalised status this service reasons about. */
    private CallStatus callStatus = CallStatus.UNKNOWN;

    /** The provider's own status, kept because the deal profile renders it verbatim. */
    private ExotelCallStatus exotelCallStatus;

    private String direction;
    private String answeredBy;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private Long conversationDuration;
    private BigDecimal price;
    private String recordingUrl;

    private ExotelCallStatus leg1Status;
    private ExotelCallStatus leg2Status;

    private Map<String, Object> exotelCallRequest;
    private Map<String, Object> exotelConnectAppletRequest;
    private Map<String, Object> exotelCallResponse;

    /** Agent note, for a call logged by hand. */
    private String note;

    public Call() {
        super();
    }

    public Call(Call other) {
        super(other);
        this.providerCallId = other.providerCallId;
        this.parentCallSid = other.parentCallSid;
        this.accountSid = other.accountSid;
        this.ticketId = other.ticketId;
        this.productId = other.productId;
        this.connectionName = other.connectionName;
        this.callProvider = other.callProvider;
        this.isOutbound = other.isOutbound;
        this.fromDialCode = other.fromDialCode;
        this.from = other.from;
        this.toDialCode = other.toDialCode;
        this.to = other.to;
        this.customerDialCode = other.customerDialCode;
        this.customerPhoneNumber = other.customerPhoneNumber;
        this.callerId = other.callerId;
        this.callStatus = other.callStatus;
        this.exotelCallStatus = other.exotelCallStatus;
        this.direction = other.direction;
        this.answeredBy = other.answeredBy;
        this.startTime = other.startTime;
        this.endTime = other.endTime;
        this.duration = other.duration;
        this.conversationDuration = other.conversationDuration;
        this.price = other.price;
        this.recordingUrl = other.recordingUrl;
        this.leg1Status = other.leg1Status;
        this.leg2Status = other.leg2Status;
        this.exotelCallRequest = other.exotelCallRequest;
        this.exotelConnectAppletRequest = other.exotelConnectAppletRequest;
        this.exotelCallResponse = other.exotelCallResponse;
        this.note = other.note;
    }

    /**
     * Folds in whatever a later event reported, leaving anything it did not mention alone.
     *
     * <p>Null-guarded field by field rather than replacing wholesale, because provider events are
     * partial and arrive out of order: a status callback carries no {@code from}, and a late one
     * must not blank what an earlier, richer event already recorded.
     */
    public Call merge(Call incoming) {
        if (incoming == null) return this;

        if (incoming.providerCallId != null) this.providerCallId = incoming.providerCallId;
        if (incoming.parentCallSid != null) this.parentCallSid = incoming.parentCallSid;
        if (incoming.accountSid != null) this.accountSid = incoming.accountSid;
        if (incoming.ticketId != null) this.ticketId = incoming.ticketId;
        if (incoming.productId != null) this.productId = incoming.productId;
        if (incoming.connectionName != null) this.connectionName = incoming.connectionName;
        if (incoming.callProvider != null) this.callProvider = incoming.callProvider;
        if (incoming.from != null) this.from = incoming.from;
        if (incoming.fromDialCode != null) this.fromDialCode = incoming.fromDialCode;
        if (incoming.to != null) this.to = incoming.to;
        if (incoming.toDialCode != null) this.toDialCode = incoming.toDialCode;
        if (incoming.customerPhoneNumber != null) this.customerPhoneNumber = incoming.customerPhoneNumber;
        if (incoming.customerDialCode != null) this.customerDialCode = incoming.customerDialCode;
        if (incoming.callerId != null) this.callerId = incoming.callerId;
        if (incoming.direction != null) this.direction = incoming.direction;
        if (incoming.answeredBy != null) this.answeredBy = incoming.answeredBy;
        if (incoming.startTime != null) this.startTime = incoming.startTime;
        if (incoming.endTime != null) this.endTime = incoming.endTime;
        if (incoming.duration != null) this.duration = incoming.duration;
        if (incoming.conversationDuration != null) this.conversationDuration = incoming.conversationDuration;
        if (incoming.price != null) this.price = incoming.price;
        if (incoming.recordingUrl != null) this.recordingUrl = incoming.recordingUrl;
        if (incoming.leg1Status != null) this.leg1Status = incoming.leg1Status;
        if (incoming.leg2Status != null) this.leg2Status = incoming.leg2Status;
        if (incoming.exotelCallRequest != null) this.exotelCallRequest = incoming.exotelCallRequest;
        if (incoming.exotelConnectAppletRequest != null)
            this.exotelConnectAppletRequest = incoming.exotelConnectAppletRequest;
        if (incoming.exotelCallResponse != null) this.exotelCallResponse = incoming.exotelCallResponse;
        if (incoming.note != null) this.note = incoming.note;

        // Status is the one field a late event must not be allowed to walk backwards. A terminal
        // state stays terminal: a stray in-progress callback arriving after completed describes a
        // moment that has already passed, not the current state of the call.
        if (incoming.exotelCallStatus != null && !this.isTerminal()) {
            this.exotelCallStatus = incoming.exotelCallStatus;
            this.callStatus = incoming.exotelCallStatus.toCallStatus();
        }

        return this;
    }

    private boolean isTerminal() {
        return this.exotelCallStatus == ExotelCallStatus.COMPLETED
                || this.exotelCallStatus == ExotelCallStatus.FAILED
                || this.exotelCallStatus == ExotelCallStatus.NO_ANSWER
                || this.exotelCallStatus == ExotelCallStatus.BUSY
                || this.exotelCallStatus == ExotelCallStatus.CANCELLED;
    }
}
