package com.fincity.saas.message.dto.call.provider.exotel;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallDetailsExtended;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelLeg;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
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
    private static final long serialVersionUID = 1L;

    private String sid;
    private String parentCallSid;
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

    public ExotelCall(ExotelCallRequest exotelCallRequest) {
        this.from = exotelCallRequest.getFrom();
        this.to = exotelCallRequest.getTo();
        this.callerId = exotelCallRequest.getCallerId();
    }

    public ExotelCall update(ExotelCallResponse exotelCallResponse) {
        if (exotelCallResponse == null || exotelCallResponse.getCall() == null) {
            return this;
        }

        var call = exotelCallResponse.getCall();

        this.sid = call.getSid();
        this.parentCallSid = call.getParentCallSid();
        this.accountSid = call.getAccountSid();
        this.from = call.getFrom();
        this.to = call.getTo();
        this.exotelCallStatus = call.getStatus();

        // Parse startTime and endTime
        if (call.getStartTime() != null) {
            try {
                this.startTime = LocalDateTime.parse(call.getStartTime().replace(" ", "T"));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (call.getEndTime() != null) {
            try {
                this.endTime = LocalDateTime.parse(call.getEndTime().replace(" ", "T"));
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        // Parse duration
        if (call.getDuration() != null) {
            try {
                this.duration = Long.valueOf(call.getDuration());
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        // Parse price
        if (call.getPrice() != null) {
            try {
                this.price = Double.valueOf(call.getPrice());
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        this.direction = call.getDirection();
        this.answeredBy = call.getAnsweredBy();
        this.recordingUrl = call.getRecordingUrl();

        // Update extended details if available
        if (call.getDetails() != null) {
            this.conversationDuration = call.getDetails().getConversationDuration() != null
                    ? Long.valueOf(call.getDetails().getConversationDuration())
                    : null;
            this.leg1Status = call.getDetails().getLeg1Status();
            this.leg2Status = call.getDetails().getLeg2Status();

            if (call.getDetails().getLegs() != null) {
                this.legs = call.getDetails().getLegs().stream()
                        .map(ExotelCallDetailsExtended.LegWrapper::getLeg)
                        .toList();
            }
        }

        return this;
    }
}
