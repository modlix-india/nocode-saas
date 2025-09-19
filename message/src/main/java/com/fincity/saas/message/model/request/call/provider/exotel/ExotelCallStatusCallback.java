package com.fincity.saas.message.model.request.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelLeg;
import com.fincity.saas.message.util.SetterUtil;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
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
        if (dataBuffer == null || objectMapper == null) {
            throw new IllegalArgumentException("DataBuffer and ObjectMapper cannot be null");
        }

        try {
            int readableBytes = dataBuffer.readableByteCount();
            if (readableBytes == 0) {
                throw new GenericException(HttpStatus.BAD_REQUEST, "Empty request body");
            }

            byte[] bytes = new byte[readableBytes];
            dataBuffer.read(bytes);
            return objectMapper.readValue(bytes, ExotelCallStatusCallback.class);
        } catch (IOException e) {
            throw new GenericException(
                    HttpStatus.BAD_REQUEST, String.format("Error parsing JSON request: %s", e.getMessage()));
        }
    }

    public static ExotelCallStatusCallback ofForm(MultiValueMap<String, String> formData) {
        if (formData == null || formData.isEmpty()) return new ExotelCallStatusCallback();

        ExotelCallStatusCallback callback = new ExotelCallStatusCallback();

        SetterUtil.setIfPresent(formData, "CallSid", callback::setCallSid);
        SetterUtil.setIfPresent(formData, "RecordingUrl", callback::setRecordingUrl);
        SetterUtil.setIfPresent(formData, "EventType", callback::setEventType);
        SetterUtil.setIfPresent(formData, "To", callback::setTo);
        SetterUtil.setIfPresent(formData, "From", callback::setFrom);
        SetterUtil.setIfPresent(formData, "PhoneNumberSid", callback::setPhoneNumberSid);
        SetterUtil.setIfPresent(formData, "Direction", callback::setDirection);
        SetterUtil.setIfPresent(formData, "CustomField", callback::setCustomField);
        SetterUtil.setIfPresent(formData, "DateUpdated", callback::setDateUpdated);
        SetterUtil.setIfPresent(formData, "DateCreated", callback::setDateCreated);
        SetterUtil.setIfPresent(formData, "StartTime", callback::setStartTime);
        SetterUtil.setIfPresent(formData, "EndTime", callback::setEndTime);

        SetterUtil.parseLongIfPresent(formData, "ConversationDuration", callback::setConversationDuration);

        SetterUtil.parseEnumIfPresent(
                formData,
                "Status",
                ExotelCallStatus.class,
                status -> status.getDisplayName().equals(formData.getFirst("Status")),
                callback::setStatus);

        return callback;
    }

    public static ExotelCallStatusCallback ofMultiPart(MultiValueMap<String, Part> formData) {
        if (formData == null || formData.isEmpty()) return new ExotelCallStatusCallback();

        ExotelCallStatusCallback callback = new ExotelCallStatusCallback();

        UnaryOperator<String> valueExtractor = key -> extractPartValue(formData.getFirst(key));

        SetterUtil.setIfPresent(valueExtractor.apply("CallSid"), callback::setCallSid);
        SetterUtil.setIfPresent(valueExtractor.apply("RecordingUrl"), callback::setRecordingUrl);
        SetterUtil.setIfPresent(valueExtractor.apply("EventType"), callback::setEventType);
        SetterUtil.setIfPresent(valueExtractor.apply("To"), callback::setTo);
        SetterUtil.setIfPresent(valueExtractor.apply("From"), callback::setFrom);
        SetterUtil.setIfPresent(valueExtractor.apply("PhoneNumberSid"), callback::setPhoneNumberSid);
        SetterUtil.setIfPresent(valueExtractor.apply("Direction"), callback::setDirection);
        SetterUtil.setIfPresent(valueExtractor.apply("CustomField"), callback::setCustomField);
        SetterUtil.setIfPresent(valueExtractor.apply("DateUpdated"), callback::setDateUpdated);
        SetterUtil.setIfPresent(valueExtractor.apply("DateCreated"), callback::setDateCreated);
        SetterUtil.setIfPresent(valueExtractor.apply("StartTime"), callback::setStartTime);
        SetterUtil.setIfPresent(valueExtractor.apply("EndTime"), callback::setEndTime);

        String conversationDurationStr = valueExtractor.apply("ConversationDuration");
        Long conversationDuration = SetterUtil.parseLong(conversationDurationStr);
        SetterUtil.setIfPresent(conversationDuration, callback::setConversationDuration);

        String status = valueExtractor.apply("Status");

        SetterUtil.parseEnum(
                status, ExotelCallStatus.class, sta -> sta.getDisplayName().equals(status), callback::setStatus);

        return callback;
    }

    private static String extractPartValue(Part part) {
        if (part instanceof FormFieldPart ffp) return StringUtil.safeIsBlank(ffp.value()) ? null : ffp.value();
        return null;
    }
}
