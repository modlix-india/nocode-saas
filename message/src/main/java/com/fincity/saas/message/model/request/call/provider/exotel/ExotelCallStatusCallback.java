package com.fincity.saas.message.model.request.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

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

    public static Mono<ExotelCallStatusCallback> fromDataBuffer(DataBuffer dataBuffer, ObjectMapper objectMapper) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            ExotelCallStatusCallback callback = objectMapper.readValue(bytes, ExotelCallStatusCallback.class);
            return Mono.just(callback);
        } catch (IOException e) {
            return Mono.error(new RuntimeException("Error parsing JSON request: " + e.getMessage()));
        }
    }

    public static ExotelCallStatusCallback fromFormData(MultiValueMap<String, String> formData) {
        ExotelCallStatusCallback callback = new ExotelCallStatusCallback();

        if (formData.containsKey("CallSid")) callback.setCallSid(formData.getFirst("CallSid"));

        if (formData.containsKey("Status")) {
            String statusStr = formData.getFirst("Status");
            for (ExotelCallStatus status : ExotelCallStatus.values()) {
                if (status.getDisplayName().equals(statusStr)) {
                    callback.setStatus(status);
                    break;
                }
            }
        }

        if (formData.containsKey("RecordingUrl")) callback.setRecordingUrl(formData.getFirst("RecordingUrl"));

        if (formData.containsKey("EventType")) callback.setEventType(formData.getFirst("EventType"));

        if (formData.containsKey("To")) callback.setTo(formData.getFirst("To"));

        if (formData.containsKey("From")) callback.setFrom(formData.getFirst("From"));

        if (formData.containsKey("PhoneNumberSid")) callback.setPhoneNumberSid(formData.getFirst("PhoneNumberSid"));

        if (formData.containsKey("Direction")) callback.setDirection(formData.getFirst("Direction"));

        if (formData.containsKey("CustomField")) callback.setCustomField(formData.getFirst("CustomField"));

        return callback;
    }
}
