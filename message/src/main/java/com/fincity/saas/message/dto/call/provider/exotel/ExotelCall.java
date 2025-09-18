package com.fincity.saas.message.dto.call.provider.exotel;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelConnectAppletRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelPassThruCallback;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallDetails;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallDetailsExtended;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelLeg;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    private static final String EXOTEL_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter EXOTEL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(EXOTEL_DATE_TIME_PATTERN);

    private String sid;
    private String parentCallSid;
    private LocalDateTime dateCreated;
    private LocalDateTime dateUpdated;
    private String accountSid;
    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;
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
    private ExotelCallResponse exotelCallResponse;

    public ExotelCall() {}

    public ExotelCall(ExotelCallRequest request) {
        this.from = request.getFrom();
        this.to = request.getTo();
        this.callerId = request.getCallerId();
        this.exotelCallRequest = request;
    }

    public ExotelCall(ExotelConnectAppletRequest request) {
        this.sid = request.getCallSid();
        this.direction = request.getDirection();
        this.from = Optional.ofNullable(request.getFrom()).orElse(this.from);
        this.to = Optional.ofNullable(request.getTo()).orElse(this.to);
        this.callerId = Optional.ofNullable(request.getCallTo()).orElse(this.callerId);
        this.startTime = parseDate(request.getStartTime());
        this.dateCreated = parseDate(request.getCreated());
    }

    private static LocalDateTime parseDate(String date) {
        try {
            return date == null ? null : LocalDateTime.parse(date, EXOTEL_DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
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

        this.sid = Optional.ofNullable(this.sid).orElse(callback.getCallSid());
        this.exotelCallStatus = Optional.ofNullable(callback.getStatus()).orElse(this.exotelCallStatus);
        this.recordingUrl = Optional.ofNullable(callback.getRecordingUrl()).orElse(this.recordingUrl);
        this.direction = Optional.ofNullable(callback.getDirection()).orElse(this.direction);
        this.from = Optional.ofNullable(callback.getFrom()).orElse(this.from);
        this.to = Optional.ofNullable(callback.getTo()).orElse(this.to);
        this.startTime = parseDate(callback.getStartTime());
        this.endTime = parseDate(callback.getEndTime());
        this.conversationDuration =
                Optional.ofNullable(callback.getConversationDuration()).orElse(this.conversationDuration);

        if (callback.getLegs() != null && !callback.getLegs().isEmpty()) this.legs = callback.getLegs();

        return this;
    }

    public ExotelCall update(ExotelPassThruCallback callback) {
        if (callback == null) return this;

        this.sid = Optional.ofNullable(this.sid).orElse(callback.getCallSid());
        this.exotelCallStatus = Optional.ofNullable(callback.getCallStatus()).orElse(this.exotelCallStatus);
        this.recordingUrl = Optional.ofNullable(callback.getRecordingUrl()).orElse(this.recordingUrl);
        this.direction = Optional.ofNullable(callback.getDirection()).orElse(this.direction);
        this.from = Optional.ofNullable(callback.getFrom()).orElse(this.from);
        this.to = Optional.ofNullable(callback.getCallTo()).orElse(this.to);
        this.callerId = Optional.ofNullable(callback.getTo()).orElse(this.callerId);
        this.startTime = parseDate(callback.getStartTime());
        this.dateCreated = parseDate(callback.getCreated());
        this.endTime = parseDate(callback.getEndTime());

        Long dialDuration = callback.getDialCallDuration();
        if (dialDuration != null) {
            this.duration = dialDuration;
            this.conversationDuration = dialDuration;
        }

        this.callerId = Optional.ofNullable(callback.getOutgoingPhoneNumber()).orElse(this.callerId);

        return this;
    }
}
