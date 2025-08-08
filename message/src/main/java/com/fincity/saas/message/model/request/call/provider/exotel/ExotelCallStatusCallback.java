package com.fincity.saas.message.model.request.call.provider.exotel;

import static com.fincity.saas.message.util.SetterUtil.parseEnumIfPresent;
import static com.fincity.saas.message.util.SetterUtil.setIfPresent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelLeg;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelCallStatusCallback implements Serializable {

    @Serial
    private static final long serialVersionUID = 3500927590328043740L;

    @JsonProperty("CallSid")
    private String callSid;

    @JsonProperty("DateUpdated")
    private String dateUpdated;

    @JsonProperty("Status")
    private ExotelCallStatus status;

    @JsonProperty("RecordingUrl")
    private String recordingUrl;

    @JsonProperty("EventType")
    private String eventType;

    @JsonProperty("DateCreated")
    private String dateCreated;

    @JsonProperty("To")
    private String to;

    @JsonProperty("From")
    private String from;

    @JsonProperty("PhoneNumberSid")
    private String phoneNumberSid;

    @JsonProperty("StartTime")
    private String startTime;

    @JsonProperty("EndTime")
    private String endTime;

    @JsonProperty("ConversationDuration")
    private Long conversationDuration;

    @JsonProperty("Direction")
    private String direction;

    @JsonProperty("CustomField")
    private String customField;

    @JsonProperty("Legs")
    private List<ExotelLeg> legs;

    public static ExotelCallStatusCallback of(DataBuffer dataBuffer, ObjectMapper objectMapper) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return objectMapper.readValue(bytes, ExotelCallStatusCallback.class);
        } catch (IOException e) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "Error parsing JSON request: " + e.getMessage());
        }
    }

    public static ExotelCallStatusCallback of(MultiValueMap<String, String> formData) {
        ExotelCallStatusCallback callback = new ExotelCallStatusCallback();

        setIfPresent(formData, "CallSid", callback::setCallSid);
        setIfPresent(formData, "RecordingUrl", callback::setRecordingUrl);
        setIfPresent(formData, "EventType", callback::setEventType);
        setIfPresent(formData, "To", callback::setTo);
        setIfPresent(formData, "From", callback::setFrom);
        setIfPresent(formData, "PhoneNumberSid", callback::setPhoneNumberSid);
        setIfPresent(formData, "Direction", callback::setDirection);
        setIfPresent(formData, "CustomField", callback::setCustomField);

        parseEnumIfPresent(
                formData,
                "Status",
                ExotelCallStatus.class,
                status -> status.getDisplayName().equals(formData.getFirst("Status")),
                callback::setStatus);

        return callback;
    }
}
