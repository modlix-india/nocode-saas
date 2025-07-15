package com.fincity.saas.message.dto.call.exotel;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.call.exotel.ExotelCallStatus;
import com.fincity.saas.message.model.request.call.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.response.call.exotel.ExotelCallResponse;
import com.fincity.saas.message.model.response.call.exotel.ExotelLeg;
import com.fincity.saas.message.util.PhoneUtil;

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

    }

    public ExotelCall update(ExotelCallResponse exotelCallResponse) {}
}
