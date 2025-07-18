package com.fincity.saas.message.model.request.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelCallType;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelRecordingChannel;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelRecordingFormat;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelStartPlaybackTo;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelStatusCallbackContentType;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelStatusCallbackEvents;
import com.fincity.saas.message.util.IClassConvertor;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelCallRequest implements Serializable, IClassConvertor {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("From")
    private String from;

    @JsonProperty("To")
    private String to;

    @JsonProperty("CallerId")
    private String callerId;

    @JsonProperty("CallType")
    private String callType = ExotelCallType.getDefault().getExotelName();

    @JsonProperty("TimeLimit")
    private Integer timeLimit;

    @JsonProperty("TimeOut")
    private Integer timeOut;

    @JsonProperty("WaitUrl")
    private String waitUrl;

    @JsonProperty("Record")
    private Boolean doRecord = Boolean.FALSE;

    @JsonProperty("RecordingChannels")
    private String recordingChannels = ExotelRecordingChannel.getDefault().getExotelName();

    @JsonProperty("RecordingFormat")
    private String recordingFormat = ExotelRecordingFormat.getDefault().getExotelName();

    @JsonProperty("StartRecordingOn")
    private String startPlaybackTo = ExotelStartPlaybackTo.getDefault().getExotelName();

    @JsonProperty("StartRecordingValue")
    private String startPlaybackValue;

    @JsonProperty("CustomField")
    private String customField;

    @JsonProperty("StatusCallback")
    private String statusCallback;

    @JsonProperty("StatusCallbackEvents")
    private String[] statusCallbackEvents = {
        ExotelStatusCallbackEvents.getDefault().getExotelName()
    };

    @JsonProperty("StatusCallbackContentType")
    private String statusCallbackContentType =
            ExotelStatusCallbackContentType.getDefault().getExotelName();

    public static ExotelCallRequest of(String to, String callerId, boolean doRecord) {
        return new ExotelCallRequest().setTo(to).setCallerId(callerId).setDoRecord(doRecord);
    }
}
