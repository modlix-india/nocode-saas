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

    public static ExotelCall ofInbound(ExotelConnectAppletRequest request, PhoneNumber to) {

        PhoneNumber from = PhoneNumber.of(request.getFrom());
        PhoneNumber callerId = PhoneNumber.of(request.getCallTo());

        return new ExotelCall()
                .setSid(request.getCallSid())
                .setDirection(ExotelDirection.INBOUND.name())
                .setFromDialCode(from.getCountryCode())
                .setFrom(from.getNumber())
                .setToDialCode(to.getCountryCode())
                .setTo(to.getNumber())
                .setCustomerDialCode(from.getCountryCode())
                .setCustomerPhoneNumber(from.getNumber())
                .setCallerId(callerId.getLandlineNumber())
                .setStartTime(parseDate(request.getStartTime()))
                .setDateCreated(parseDate(request.getCreated()))
                .setRecordingUrl(request.getRecordingUrl())
                .setExotelConnectAppletRequest(request);
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

        if (this.sid == null) SetterUtil.setIfPresent(callback.getCallSid(), this::setSid);

        SetterUtil.setIfPresent(callback.getStatus(), this::setExotelCallStatus);
        SetterUtil.setIfPresent(callback.getRecordingUrl(), this::setRecordingUrl);
        SetterUtil.setIfPresent(callback.getDirection(), this::setDirection);
        SetterUtil.setIfPresent(callback.getFrom(), this::setFrom);
        SetterUtil.setIfPresent(callback.getTo(), this::setTo);
        if (callback.getStartTime() != null) this.startTime = parseDate(callback.getStartTime());
        if (callback.getEndTime() != null) this.endTime = parseDate(callback.getEndTime());
        SetterUtil.setIfPresent(callback.getConversationDuration(), this::setConversationDuration);

        if (callback.getLegs() != null && !callback.getLegs().isEmpty()) this.legs = callback.getLegs();

        return this;
    }

    public ExotelCall update(ExotelPassThruCallback callback) {
        if (callback == null) return this;

        if (this.sid == null) SetterUtil.setIfPresent(callback.getCallSid(), this::setSid);

        SetterUtil.setIfPresent(callback.getCallStatus(), this::setExotelCallStatus);
        SetterUtil.setIfPresent(callback.getRecordingUrl(), this::setRecordingUrl);
        SetterUtil.setIfPresent(callback.getDirection(), this::setDirection);
        SetterUtil.setIfPresent(callback.getFrom(), this::setFrom);
        SetterUtil.setIfPresent(callback.getCallTo(), this::setTo);
        SetterUtil.setIfPresent(callback.getTo(), this::setCallerId);

        if (callback.getStartTime() != null) this.startTime = parseDate(callback.getStartTime());

        if (callback.getEndTime() != null) this.endTime = parseDate(callback.getEndTime());

        if (callback.getCreated() != null) this.dateCreated = parseDate(callback.getCreated());

        Long dialDuration = callback.getDialCallDuration();
        if (dialDuration != null) {
            this.duration = dialDuration;
            this.conversationDuration = dialDuration;
        }

        SetterUtil.setIfPresent(callback.getOutgoingPhoneNumber(), this::setCallerId);

        return this;
    }
}
