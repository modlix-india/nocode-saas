package com.fincity.saas.message.model.request.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelLeg;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelCallStatusCallback implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("CallSid")
    private String callSid;

    @JsonProperty("DateUpdated")
    private LocalDateTime dateUpdated;

    @JsonProperty("Status")
    private ExotelCallStatus status;

    @JsonProperty("RecordingUrl")
    private String recordingUrl;

    @JsonProperty("EventType")
    private String eventType;

    @JsonProperty("DateCreated")
    private LocalDateTime dateCreated;

    @JsonProperty("To")
    private String to;

    @JsonProperty("From")
    private String from;

    @JsonProperty("PhoneNumberSid")
    private String phoneNumberSid;

    @JsonProperty("StartTime")
    private LocalDateTime startTime;

    @JsonProperty("EndTime")
    private LocalDateTime endTime;

    @JsonProperty("ConversationDuration")
    private Integer conversationDuration;

    @JsonProperty("Direction")
    private String direction;

    @JsonProperty("CustomField")
    private String customField;

    @JsonProperty("Legs")
    private List<ExotelLeg> legs;
}
