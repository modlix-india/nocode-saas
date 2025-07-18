package com.fincity.saas.message.model.request.call.provider.exotel;

import static com.fincity.saas.message.util.SetterUtil.parseLong;
import static com.fincity.saas.message.util.SetterUtil.setIfPresent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelConnectAppletRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 2774681647500516971L;

    @JsonProperty("CallSid")
    private String callSid;

    @JsonProperty("CallFrom")
    private String callFrom;

    @JsonProperty("CallTo")
    private String callTo;

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

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("From")
    private String from;

    @JsonProperty("To")
    private String to;

    @JsonProperty("CurrentTime")
    private String currentTime;

    @JsonProperty("DialCallStatus")
    private String dialCallStatus;

    @JsonProperty("digits")
    private String digits;

    @JsonProperty("CustomField")
    private String customField;

    @JsonProperty("RecordingUrl")
    private String recordingUrl;

    public static ExotelConnectAppletRequest of(Map<String, Object> map) {
        ExotelConnectAppletRequest req = new ExotelConnectAppletRequest();

        setIfPresent(map, "CallSid", req::setCallSid);
        setIfPresent(map, "CallFrom", req::setCallFrom);
        setIfPresent(map, "CallTo", req::setCallTo);
        setIfPresent(map, "Direction", req::setDirection);
        setIfPresent(map, "Created", req::setCreated);
        setIfPresent(map, "StartTime", req::setStartTime);
        setIfPresent(map, "EndTime", req::setEndTime);
        setIfPresent(map, "CallType", req::setCallType);
        setIfPresent(map, "DialWhomNumber", req::setDialWhomNumber);
        setIfPresent(map, "flow_id", req::setFlowId);
        setIfPresent(map, "From", req::setFrom);
        setIfPresent(map, "To", req::setTo);
        setIfPresent(map, "CurrentTime", req::setCurrentTime);
        setIfPresent(map, "DialCallStatus", req::setDialCallStatus);
        setIfPresent(map, "CustomField", req::setCustomField);
        setIfPresent(map, "RecordingUrl", req::setRecordingUrl);

        Object digitObj = map.get("digits");
        if (digitObj != null) {
            String digits = digitObj.toString().trim();
            if (digits.startsWith("\"") && digits.endsWith("\"")) digits = digits.substring(1, digits.length() - 1);
            req.setDigits(digits);
        }

        req.setDialCallDuration(parseLong(map.get("DialCallDuration")));

        return req;
    }
}
