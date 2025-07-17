package com.fincity.saas.message.model.request.call.provider.exotel;

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
    private static final long serialVersionUID = 1L;

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

    public static ExotelPassThruCallback fromFormData(MultiValueMap<String, String> formData) {
        ExotelPassThruCallback callback = new ExotelPassThruCallback();

        if (formData.containsKey("CallSid")) callback.setCallSid(formData.getFirst("CallSid"));
        if (formData.containsKey("CallFrom")) callback.setCallFrom(formData.getFirst("CallFrom"));
        if (formData.containsKey("CallTo")) callback.setCallTo(formData.getFirst("CallTo"));

        if (formData.containsKey("CallStatus")) {
            String statusStr = formData.getFirst("CallStatus");
            for (ExotelCallStatus status : ExotelCallStatus.values()) {
                if (status.getDisplayName().equals(statusStr)) {
                    callback.setCallStatus(status);
                    break;
                }
            }
        }

        if (formData.containsKey("Direction")) callback.setDirection(formData.getFirst("Direction"));
        if (formData.containsKey("Created")) callback.setCreated(formData.getFirst("Created"));

        if (formData.containsKey("DialCallDuration")) {
            try {
                callback.setDialCallDuration(Long.parseLong(formData.getFirst("DialCallDuration")));
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }

        if (formData.containsKey("StartTime")) callback.setStartTime(formData.getFirst("StartTime"));
        if (formData.containsKey("EndTime")) callback.setEndTime(formData.getFirst("EndTime"));
        if (formData.containsKey("CallType")) callback.setCallType(formData.getFirst("CallType"));
        if (formData.containsKey("DialWhomNumber")) callback.setDialWhomNumber(formData.getFirst("DialWhomNumber"));
        if (formData.containsKey("From")) callback.setFrom(formData.getFirst("From"));
        if (formData.containsKey("To")) callback.setTo(formData.getFirst("To"));
        if (formData.containsKey("CurrentTime")) callback.setCurrentTime(formData.getFirst("CurrentTime"));

        // Optional parameters
        if (formData.containsKey("DialCallStatus")) {
            String statusStr = formData.getFirst("DialCallStatus");
            for (ExotelCallStatus status : ExotelCallStatus.values()) {
                if (status.getDisplayName().equals(statusStr)) {
                    callback.setDialCallStatus(status);
                    break;
                }
            }
        }

        if (formData.containsKey("digits")) {
            String digits = formData.getFirst("digits");
            if (digits != null) {
                digits = digits.trim();
                if (digits.startsWith("\"") && digits.endsWith("\"")) {
                    digits = digits.substring(1, digits.length() - 1);
                }
                callback.setDigits(digits);
            }
        }

        if (formData.containsKey("CustomField")) callback.setCustomField(formData.getFirst("CustomField"));
        if (formData.containsKey("RecordingUrl")) callback.setRecordingUrl(formData.getFirst("RecordingUrl"));
        if (formData.containsKey("OutgoingPhoneNumber"))
            callback.setOutgoingPhoneNumber(formData.getFirst("OutgoingPhoneNumber"));

        return callback;
    }
}
