package com.fincity.saas.message.model.request.call.provider.exotel;

import static com.fincity.saas.message.util.SetterUtil.parseEnumIfPresent;
import static com.fincity.saas.message.util.SetterUtil.parseLongIfPresent;
import static com.fincity.saas.message.util.SetterUtil.setIfPresent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
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
    private ExotelCallStatus callStatus;

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
    private ExotelCallStatus dialCallStatus;

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
        ExotelPassThruCallback callback = new ExotelPassThruCallback();

        setIfPresent(formData, "CallSid", callback::setCallSid);
        setIfPresent(formData, "CallFrom", callback::setCallFrom);
        setIfPresent(formData, "CallTo", callback::setCallTo);
        setIfPresent(formData, "Direction", callback::setDirection);
        setIfPresent(formData, "Created", callback::setCreated);
        setIfPresent(formData, "StartTime", callback::setStartTime);
        setIfPresent(formData, "EndTime", callback::setEndTime);
        setIfPresent(formData, "CallType", callback::setCallType);
        setIfPresent(formData, "DialWhomNumber", callback::setDialWhomNumber);
        setIfPresent(formData, "From", callback::setFrom);
        setIfPresent(formData, "To", callback::setTo);
        setIfPresent(formData, "CurrentTime", callback::setCurrentTime);
        setIfPresent(formData, "CustomField", callback::setCustomField);
        setIfPresent(formData, "RecordingUrl", callback::setRecordingUrl);
        setIfPresent(formData, "OutgoingPhoneNumber", callback::setOutgoingPhoneNumber);

        parseLongIfPresent(formData, "DialCallDuration", callback::setDialCallDuration);

        parseEnumIfPresent(
                formData,
                "CallStatus",
                ExotelCallStatus.class,
                status -> status.getDisplayName().equals(formData.getFirst("CallStatus")),
                callback::setCallStatus);

        parseEnumIfPresent(
                formData,
                "DialCallStatus",
                ExotelCallStatus.class,
                status -> status.getDisplayName().equals(formData.getFirst("DialCallStatus")),
                callback::setDialCallStatus);

        String digits = formData.getFirst("digits");
        if (digits != null) {
            digits = digits.trim();
            if (digits.startsWith("\"") && digits.endsWith("\"")) digits = digits.substring(1, digits.length() - 1);
            callback.setDigits(digits);
        }

        return callback;
    }
}
