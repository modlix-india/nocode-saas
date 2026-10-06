package com.fincity.saas.message.dto.call.provider.exotel;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelDirection;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelConnectAppletRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelPassThruCallback;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallDetails;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallDetailsExtended;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelLeg;
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.SetterUtil;
import java.io.Serial;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ExotelCall extends BaseUpdatableDto<ExotelCall> {

    @Serial
    private static final long serialVersionUID = 6195102404059168734L;

    /**
     * The space-separated form, with the offset optional: the telephony API sends {@code 2026-09-04 18:49:45}, the
     * integrations engine {@code 2026-09-08 18:06:59+05:30} on browser calls.
     */
    private static final String EXOTEL_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss[XXX]";

    private static final DateTimeFormatter EXOTEL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(EXOTEL_DATE_TIME_PATTERN);

    private String sid;
    private String parentCallSid;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
    private String accountSid;

    /**
     * Which service owns this call, the call-side twin of {@code OWNER_SERVICE} on a WhatsApp phone
     * number. Stamped when the call is created, because both entry points are initiated by the
     * owning service, and read back when a status callback arrives carrying nothing but a Sid.
     */
    private String ownerService;

    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;

    private Integer customerDialCode = PhoneUtil.getDefaultCallingCode();
    private String customerPhoneNumber;

    private String callerId;
    private ExotelCallStatus exotelCallStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private Double price;
    private String direction;
    private String answeredBy;
    private String recordingUrl;
    private Long conversationDuration;
    private ExotelCallStatus leg1Status;
    private ExotelCallStatus leg2Status;
    private List<ExotelLeg> legs;
    private ExotelCallRequest exotelCallRequest;
    private ExotelConnectAppletRequest exotelConnectAppletRequest;
    private ExotelCallResponse exotelCallResponse;

    public static ExotelCall ofOutbound(ExotelCallRequest request) {

        PhoneNumber from = PhoneNumber.of(request.getFrom());
        PhoneNumber to = PhoneNumber.of(request.getTo());

        return new ExotelCall()
                .setFromDialCode(from.getCountryCode())
                .setFrom(from.getNumber())
                .setToDialCode(to.getCountryCode())
                .setTo(to.getNumber())
                .setCustomerDialCode(to.getCountryCode())
                .setCustomerPhoneNumber(to.getNumber())
                .setCallerId(request.getCallerId())
                .setStartTime(LocalDateTime.now())
                .setExotelCallRequest(request);
    }

    public static ExotelCall ofInbound(ExotelConnectAppletRequest request, PhoneNumber to, String accountSid) {

        String fromRaw = request.getFrom() != null ? request.getFrom() : request.getCallFrom();
        String toRaw = request.getCallTo() != null ? request.getCallTo() : request.getTo();

        PhoneNumber from = PhoneNumber.of(fromRaw);
        PhoneNumber callerId = PhoneNumber.of(toRaw);

        return new ExotelCall()
                .setSid(request.getCallSid())
                .setAccountSid(accountSid)
                .setDirection(ExotelDirection.INBOUND.name())
                .setFromDialCode(from != null ? from.getCountryCode() : null)
                .setFrom(from != null ? from.getNumber() : fromRaw)
                .setToDialCode(to != null ? to.getCountryCode() : null)
                .setTo(to != null ? to.getNumber() : null)
                .setCustomerDialCode(from != null ? from.getCountryCode() : null)
                .setCustomerPhoneNumber(from != null ? from.getNumber() : fromRaw)
                .setCallerId(callerIdOf(callerId, toRaw))
                .setStartTime(parseDate(request.getStartTime()))
                .setDateCreated(parseDate(request.getCreated()))
                .setRecordingUrl(request.getRecordingUrl())
                .setExotelConnectAppletRequest(request);
    }

    /**
     * The number the customer dialled: the landline form, else the plain number, else the raw value, since on a
     * browser leg this can be a SIP URI that {@link PhoneNumber#of} cannot parse.
     */
    private static String callerIdOf(PhoneNumber callerId, String raw) {

        if (callerId == null) return raw;

        return callerId.getLandlineNumber() != null ? callerId.getLandlineNumber() : callerId.getNumber();
    }

    /**
     * Reads either timestamp shape the provider sends: local time with no zone from the telephony API, or an ISO
     * offset form from the WebRTC callback. The offset is discarded rather than converted, because every other
     * timestamp in these tables is local and both forms carry the wall-clock time the provider's dashboard shows.
     */
    private static LocalDateTime parseDate(String date) {

        if (date == null || date.isBlank()) return null;

        LocalDateTime parsed = parseTimestamp(date);
        return isEpochPlaceholder(parsed) ? null : parsed;
    }

    private static LocalDateTime parseTimestamp(String date) {

        try {
            return LocalDateTime.parse(date, EXOTEL_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Not the telephony API's format; try the WebRTC callback's.
        }

        try {
            return OffsetDateTime.parse(date).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Neither form matched.
        }

        return null;
    }

    /**
     * Whether a parsed timestamp is the provider's "not set": the passthru callback sends
     * {@code 1970-01-01 05:30:00} and {@code 0001-01-01T00:00:00Z} for times it does not have.
     */
    private static boolean isEpochPlaceholder(LocalDateTime value) {
        return value != null && value.getYear() <= 1970;
    }

    /**
     * Discards an end time that precedes its own start, as seen on a live WebRTC callback. The end is dropped
     * because the start is corroborated by when the call was requested; duration falls back to talk time.
     */
    private void dropEndBeforeStart() {

        if (this.startTime == null || this.endTime == null || !this.endTime.isBefore(this.startTime)) return;

        this.endTime = null;
    }

    private static Double parseDouble(String value) {
        try {
            return value == null ? null : Double.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    public ExotelCall update(ExotelCallResponse response) {
        ExotelCallDetails call =
                Optional.ofNullable(response).map(ExotelCallResponse::getCall).orElse(null);
        if (call == null) return this;

        return this.setSid(call.getSid())
                .setParentCallSid(call.getParentCallSid())
                .setDateCreated(parseDate(call.getDateCreated()))
                .setDateUpdated(parseDate(call.getDateUpdated()))
                .setAccountSid(call.getAccountSid())
                .setExotelCallStatus(call.getStatus())
                .setStartTime(parseDate(call.getStartTime()))
                .setEndTime(parseDate(call.getEndTime()))
                .setDuration(call.getDuration())
                .setPrice(parseDouble(call.getPrice()))
                .setDirection(call.getDirection())
                .setAnsweredBy(call.getAnsweredBy())
                .setRecordingUrl(call.getRecordingUrl())
                .updateDetails(call.getDetails())
                .setExotelCallResponse(response);
    }

    public ExotelCall updateDetails(ExotelCallDetailsExtended details) {
        if (details == null) return this;

        return this.setConversationDuration(details.getConversationDuration())
                .setLeg1Status(details.getLeg1Status())
                .setLeg2Status(details.getLeg2Status())
                .setLegs(Optional.ofNullable(details.getLegs())
                        .map(l -> l.stream()
                                .map(ExotelCallDetailsExtended.LegWrapper::getLeg)
                                .toList())
                        .orElse(null));
    }

    public ExotelCall update(ExotelCallStatusCallback callback) {
        if (callback == null) return this;

        if (this.sid == null) SetterUtil.setIfPresent(callback.getCallSid(), this::setSid);

        SetterUtil.setIfPresent(callback.getStatus(), this::setExotelCallStatus);
        SetterUtil.setIfPresent(callback.getRecordingUrl(), this::setRecordingUrl);
        SetterUtil.setIfPresent(callback.getDirection(), this::setDirection);
        // Assigned only when the value parses, so a bad value cannot erase the start time the dial recorded.
        SetterUtil.setIfPresent(parseDate(callback.getStartTime()), this::setStartTime);
        SetterUtil.setIfPresent(parseDate(callback.getEndTime()), this::setEndTime);

        this.dropEndBeforeStart();
        SetterUtil.setIfPresent(callback.getConversationDuration(), this::setConversationDuration);

        // Total is derived, not copied: the WebRTC callback reports only connected time, while total includes
        // ringing. Falls back to conversation duration only when there is nothing to derive from.
        if (this.duration == null || this.duration == 0) {
            if (this.startTime != null && this.endTime != null && !this.endTime.isBefore(this.startTime))
                this.setDuration(Duration.between(this.startTime, this.endTime).toSeconds());
            else SetterUtil.setIfPresent(callback.getConversationDuration(), this::setDuration);
        }

        if (callback.getLegs() != null && !callback.getLegs().isEmpty()) this.legs = callback.getLegs();

        return this;
    }

    public ExotelCall update(ExotelPassThruCallback callback) {
        if (callback == null) return this;

        if (this.sid == null) SetterUtil.setIfPresent(callback.getCallSid(), this::setSid);

        // CallStatus first, then DialCallStatus: the flow-builder passthru reports an answered inbound as
        // DialCallStatus "completed" with no CallStatus at all.
        SetterUtil.setIfPresent(callback.getCallStatus(), this::setExotelCallStatus);
        if (this.exotelCallStatus == null || ExotelCallStatus.IN_PROGRESS.equals(this.exotelCallStatus))
            SetterUtil.setIfPresent(callback.getDialCallStatus(), this::setExotelCallStatus);

        SetterUtil.setIfPresent(callback.getRecordingUrl(), this::setRecordingUrl);
        SetterUtil.setIfPresent(callback.getDirection(), this::setDirection);

        // Where the call landed, which for a browser agent is their SIP endpoint.
        SetterUtil.setIfPresent(callback.getDialWhomNumber(), this::setTo);

        if (callback.getStartTime() != null) this.startTime = parseDate(callback.getStartTime());

        if (callback.getEndTime() != null) this.endTime = parseDate(callback.getEndTime());

        this.dropEndBeforeStart();

        if (callback.getCreated() != null) this.dateCreated = parseDate(callback.getCreated());

        // DialCallDuration includes ringing; the answered leg's OnCallDuration is talk time.
        SetterUtil.setIfPresent(callback.getDialCallDuration(), this::setDuration);

        Long talkTime = answeredLegDuration(callback.getLegs());
        this.conversationDuration = talkTime != null ? talkTime : callback.getDialCallDuration();

        SetterUtil.setIfPresent(callback.getOutgoingPhoneNumber(), this::setCallerId);

        return this;
    }

    /**
     * The answered leg's on-call time: a sequential dial reports a leg per destination and unanswered ones carry
     * zero, so the maximum is the answered one. Null when nothing was answered.
     */
    private static Long answeredLegDuration(List<Map<String, Object>> legs) {

        if (legs == null || legs.isEmpty()) return null;

        Long longest = null;

        for (Map<String, Object> leg : legs) {
            if (leg == null) continue;
            Object value = leg.get("OnCallDuration");
            if (value == null) continue;
            try {
                long seconds = Long.parseLong(value.toString().trim());
                if (seconds > 0 && (longest == null || seconds > longest)) longest = seconds;
            } catch (NumberFormatException ignored) {
                // A leg without a readable duration tells us nothing; the others still might.
            }
        }

        return longest;
    }
}
