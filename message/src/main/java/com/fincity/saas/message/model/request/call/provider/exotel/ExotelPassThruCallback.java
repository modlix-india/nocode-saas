package com.fincity.saas.message.model.request.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.util.SetterUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.springframework.util.MultiValueMap;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelPassThruCallback implements Serializable {

    @Serial
    private static final long serialVersionUID = 8142641621599260894L;

    @JsonProperty("CallSid")
    private String callSid;

    @JsonProperty("CallFrom")
    private String callFrom;

    @JsonProperty("CallTo")
    private String callTo;

    @JsonProperty("CallStatus")
    private String callStatus;

    @JsonProperty("Direction")
    private String direction;

    @JsonProperty("Created")
    private String created;

    @JsonProperty("DialCallDuration")
    private Long dialCallDuration;

    @JsonProperty("StartTime")
    private String startTime;

    @JsonProperty("EndTime")
    private String endTime;

    @JsonProperty("CallType")
    private String callType;

    @JsonProperty("DialWhomNumber")
    private String dialWhomNumber;

    @JsonProperty("From")
    private String from;

    @JsonProperty("To")
    private String to;

    @JsonProperty("CurrentTime")
    private String currentTime;

    @JsonProperty("DialCallStatus")
    private String dialCallStatus;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("EventType")
    private String eventType;

    @JsonProperty("Legs")
    private List<Map<String, Object>> legs;

    @JsonProperty("digits")
    private String digits;

    @JsonProperty("CustomField")
    private String customField;

    @JsonProperty("RecordingUrl")
    private String recordingUrl;

    @JsonProperty("OutgoingPhoneNumber")
    private String outgoingPhoneNumber;

    public static ExotelPassThruCallback of(MultiValueMap<String, String> formData) {
        if (formData == null || formData.isEmpty())
            return new ExotelPassThruCallback();

        ExotelPassThruCallback callback = new ExotelPassThruCallback();

        SetterUtil.setIfPresent(formData, "CallSid", callback::setCallSid);
        SetterUtil.setIfPresent(formData, "CallFrom", callback::setCallFrom);
        SetterUtil.setIfPresent(formData, "CallTo", callback::setCallTo);
        SetterUtil.setIfPresent(formData, "Direction", callback::setDirection);
        SetterUtil.setIfPresent(formData, "Created", callback::setCreated);
        SetterUtil.setIfPresent(formData, "StartTime", callback::setStartTime);
        SetterUtil.setIfPresent(formData, "EndTime", callback::setEndTime);
        SetterUtil.setIfPresent(formData, "CallType", callback::setCallType);
        SetterUtil.setIfPresent(formData, "DialWhomNumber", callback::setDialWhomNumber);
        SetterUtil.setIfPresent(formData, "From", callback::setFrom);
        SetterUtil.setIfPresent(formData, "To", callback::setTo);
        SetterUtil.setIfPresent(formData, "CurrentTime", callback::setCurrentTime);
        SetterUtil.setIfPresent(formData, "CustomField", callback::setCustomField);
        SetterUtil.setIfPresent(formData, "RecordingUrl", callback::setRecordingUrl);
        SetterUtil.setIfPresent(formData, "OutgoingPhoneNumber", callback::setOutgoingPhoneNumber);
        SetterUtil.setIfPresent(formData, "CallStatus", callback::setCallStatus);
        SetterUtil.setIfPresent(formData, "DialCallStatus", callback::setDialCallStatus);
        SetterUtil.setIfPresent(formData, "Status", callback::setStatus);
        SetterUtil.setIfPresent(formData, "EventType", callback::setEventType);

        SetterUtil.parseLongIfPresent(formData, "DialCallDuration", callback::setDialCallDuration);

        String digits = formData.getFirst("digits");
        if (digits != null) {
            digits = digits.trim();
            if (digits.startsWith("\"") && digits.endsWith("\""))
                digits = digits.substring(1, digits.length() - 1);
            callback.setDigits(digits);
        }

        return callback;
    }
}
