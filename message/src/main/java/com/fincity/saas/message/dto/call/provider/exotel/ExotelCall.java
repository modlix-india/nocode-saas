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
     * The space-separated form, with the offset optional.
     *
     * <p>Optional because the provider sends both: {@code 2026-09-04 18:49:45} from the telephony
     * API, and {@code 2026-09-08 18:06:59+05:30} from the integrations engine — a third shape
     * alongside the ISO {@code T} form, and the one that arrives on browser calls. A strict pattern
     * rejected it, and the caller then assigned the resulting null over a start time the dial had
     * already recorded correctly.
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
     * Which service owns this call, the call-side twin of {@code OWNER_SERVICE} on
     * a WhatsApp phone
     * number. Stamped when the call is created, because both entry points are
     * initiated by the
     * owning service, and read back when a status callback arrives carrying nothing
     * but a Sid.
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
     * The number the customer dialled to reach us.
     *
     * <p>Prefers the landline form, because an Exotel virtual number is one and that is the shape
     * the rest of the tenant's configuration records it in. Falls back to the plain number, and
     * then to the raw value: {@link PhoneNumber#of} returns null for anything it cannot parse as a
     * phone number, and on a browser leg this field can carry a SIP URI. Storing that raw beats
     * storing nothing, since it is the only record of where the call arrived.
     */
    private static String callerIdOf(PhoneNumber callerId, String raw) {

        if (callerId == null) return raw;

        return callerId.getLandlineNumber() != null ? callerId.getLandlineNumber() : callerId.getNumber();
    }

    /**
     * Reads either timestamp shape the provider sends.
     *
     * <p>Two, and they arrive on different callbacks. The telephony API reports local time with no
     * zone — {@code 2026-09-04 16:49:20} — while the WebRTC callback reports an offset,
     * {@code 2026-09-04T19:21:01+05:30}. Parsing only the first leaves every browser call with null
     * start and end times, which is invisible until someone asks how long a call took.
     *
     * <p>The offset is discarded rather than converted: every other timestamp in these tables is
     * local, and mixing the two silently would shift durations by the offset. Both observed forms
     * carry the same wall-clock time the provider's own dashboard shows.
     *
     * <p>An unparseable value returns null and says so. Returning null quietly was how a
     * format change would present as calls with no times and nothing to explain it.
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
     * Whether a parsed timestamp is the provider's way of saying "not set".
     *
     * <p>The passthru callback sends {@code 1970-01-01 05:30:00} and {@code 0001-01-01T00:00:00Z}
     * for times it does not have, and both parse perfectly well into a date nobody meant. Stored,
     * they turn any duration computed from a start and an end into nonsense — including a negative
     * one, once the end predates the start.
     */
    private static boolean isEpochPlaceholder(LocalDateTime value) {
        return value != null && value.getYear() <= 1970;
    }

    /**
     * Discards an end time that precedes its own start.
     *
     * <p>Observed on a live WebRTC callback: a call reported as starting 22:51:24 and ending
     * 22:51:16. One of the two is wrong and the payload gives no way to tell which, so the pair is
     * not a span — it is a signal that the provider's clocks disagree.
     *
     * <p>The end is dropped rather than the start, because the start is corroborated: it is close to
     * when this service asked for the call. Keeping the pair would mean storing a call that ended
     * before it began, and any duration later derived from it would be negative. Duration itself is
     * unaffected — it falls back to the reported talk time, which is why this shows up as a
     * questionable timestamp rather than a questionable call.
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
        // Assigned only when the value parses. A failed parse used to be written as null, which
        // erased the start time the dial recorded and left the total duration to fall back to talk
        // time — a call reported as shorter than it was, from a callback that looked fine.
        SetterUtil.setIfPresent(parseDate(callback.getStartTime()), this::setStartTime);
        SetterUtil.setIfPresent(parseDate(callback.getEndTime()), this::setEndTime);

        this.dropEndBeforeStart();
        SetterUtil.setIfPresent(callback.getConversationDuration(), this::setConversationDuration);

        // Total duration is derived, not copied from conversation duration. The WebRTC callback
        // reports only the connected time — a call from 19:21:01 to 19:21:27 came back as 13 — so
        // taking that as the total silently halves how long the call actually occupied the agent.
        // The two are different measures and the schema keeps both: total includes ringing,
        // conversation is talk time.
        //
        // Falls back to the conversation duration only when there is nothing to derive from, since
        // an under-reported total still beats a null one.
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

        // CallStatus first, DialCallStatus second. The flow-builder passthru reports the outcome of
        // the dial rather than of the call — an inbound answered by the agent arrives with
        // DialCallStatus "completed" and no CallStatus at all, which left every inbound call sitting
        // at the status it was created with.
        SetterUtil.setIfPresent(callback.getCallStatus(), this::setExotelCallStatus);
        if (this.exotelCallStatus == null || ExotelCallStatus.IN_PROGRESS.equals(this.exotelCallStatus))
            SetterUtil.setIfPresent(callback.getDialCallStatus(), this::setExotelCallStatus);

        SetterUtil.setIfPresent(callback.getRecordingUrl(), this::setRecordingUrl);
        SetterUtil.setIfPresent(callback.getDirection(), this::setDirection);

        // Where the call actually landed, which for a browser agent is their SIP endpoint rather
        // than the number the connect applet was originally answered with.
        SetterUtil.setIfPresent(callback.getDialWhomNumber(), this::setTo);

        if (callback.getStartTime() != null) this.startTime = parseDate(callback.getStartTime());

        if (callback.getEndTime() != null) this.endTime = parseDate(callback.getEndTime());

        this.dropEndBeforeStart();

        if (callback.getCreated() != null) this.dateCreated = parseDate(callback.getCreated());

        // Two different measures, and this callback reports both. DialCallDuration covers the whole
        // dial including ringing; the answered leg's OnCallDuration is the time anyone actually
        // spoke. Copying the first into both — as this did — over-reports talk time by however long
        // the phone rang.
        SetterUtil.setIfPresent(callback.getDialCallDuration(), this::setDuration);

        Long talkTime = answeredLegDuration(callback.getLegs());
        this.conversationDuration = talkTime != null ? talkTime : callback.getDialCallDuration();

        SetterUtil.setIfPresent(callback.getOutgoingPhoneNumber(), this::setCallerId);

        return this;
    }

    /**
     * The longest leg's on-call time, which is the one that was answered.
     *
     * <p>A sequential dial reports a leg per destination tried, and the ones that were not answered
     * carry a zero. Taking the maximum picks the answered leg without needing to know which position
     * it occupied, and returns null when nothing was answered at all rather than a misleading zero.
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
