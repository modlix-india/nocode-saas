package com.fincity.saas.message.model.request.call.exotel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.exotel.ExotelOptions;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExotelCallRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("From")
    private String from;

    @JsonProperty("To")
    private String to;

    @JsonProperty("CallerId")
    private String callerId;

    @JsonProperty("CallType")
    private String callType;

    @JsonProperty("TimeLimit")
    private Integer timeLimit;

    @JsonProperty("TimeOut")
    private Integer timeOut;

    @JsonProperty("WaitUrl")
    private String waitUrl;

    @JsonProperty("Record")
    private Boolean doRecord = Boolean.FALSE;

    @JsonProperty("RecordingChannels")
    private String recordingChannels = ExotelOptions.RecordingChannel.getDefault();

    @JsonProperty("RecordingFormat")
    private String recordingFormat = ExotelOptions.RecordingFormat.getDefault();

    @JsonProperty("StatusCallback")
    private String statusCallback;

    @JsonProperty("StatusCallbackEvents")
    private String[] statusCallbackEvents = {ExotelOptions.StatusCallbackEvents.getDefault()};

    @JsonProperty("StatusCallbackContentType")
    private String statusCallbackContentType = ExotelOptions.StatusCallbackContentType.JSON.getExotelName();

    @JsonProperty("CustomField")
    private String customField;

    public static ExotelCallRequest of(String from, String to, String callerId) {
        return new ExotelCallRequest().setFrom(from).setTo(to).setCallerId(callerId);
    }

    public static ExotelCallRequest of(String from, String to, String callerId, boolean doRecord) {
        return new ExotelCallRequest()
                .setFrom(from)
                .setTo(to)
                .setCallerId(callerId)
                .setDoRecord(doRecord);
    }
}
