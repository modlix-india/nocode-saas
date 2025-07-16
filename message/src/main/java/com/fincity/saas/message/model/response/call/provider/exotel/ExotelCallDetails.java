package com.fincity.saas.message.model.response.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExotelCallDetails implements Serializable {

    @Serial
    private static final long serialVersionUID = 9007784384148736675L;

    @JsonProperty("Sid")
    private String sid;

    @JsonProperty("ParentCallSid")
    private String parentCallSid;

    @JsonProperty("DateCreated")
    private LocalDateTime dateCreated;

    @JsonProperty("DateUpdated")
    private LocalDateTime dateUpdated;

    @JsonProperty("AccountSid")
    private String accountSid;

    @JsonProperty("To")
    private String to;

    @JsonProperty("From")
    private String from;

    @JsonProperty("PhoneNumberSid")
    private String phoneNumberSid;

    @JsonProperty("Status")
    private ExotelCallStatus status;

    @JsonProperty("StartTime")
    private String startTime;

    @JsonProperty("EndTime")
    private String endTime;

    @JsonProperty("Duration")
    private Integer duration;

    @JsonProperty("Price")
    private String price;

    @JsonProperty("Direction")
    private String direction;

    @JsonProperty("AnsweredBy")
    private String answeredBy;

    @JsonProperty("ForwardedFrom")
    private String forwardedFrom;

    @JsonProperty("CallerName")
    private String callerName;

    @JsonProperty("Uri")
    private String uri;

    @JsonProperty("RecordingUrl")
    private String recordingUrl;

    @JsonProperty("PreSignedRecordingUrl")
    private String preSignedRecordingUrl;

    @JsonProperty("Details")
    private ExotelCallDetailsExtended details;
}
