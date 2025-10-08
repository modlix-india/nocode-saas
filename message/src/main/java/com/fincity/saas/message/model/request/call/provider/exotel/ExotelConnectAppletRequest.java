package com.fincity.saas.message.model.request.call.provider.exotel;

import static com.fincity.saas.message.util.SetterUtil.parseLong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.util.SetterUtil;
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

    public static ExotelConnectAppletRequest of(Map<String, String> map) {
        ExotelConnectAppletRequest req = new ExotelConnectAppletRequest();

        SetterUtil.setIfPresent(map, "CallSid", req::setCallSid);
        SetterUtil.setIfPresent(map, "CallFrom", req::setCallFrom);
        SetterUtil.setIfPresent(map, "CallTo", req::setCallTo);
        SetterUtil.setIfPresent(map, "Direction", req::setDirection);
        SetterUtil.setIfPresent(map, "Created", req::setCreated);
        SetterUtil.setIfPresent(map, "StartTime", req::setStartTime);
        SetterUtil.setIfPresent(map, "EndTime", req::setEndTime);
        SetterUtil.setIfPresent(map, "CallType", req::setCallType);
        SetterUtil.setIfPresent(map, "DialWhomNumber", req::setDialWhomNumber);
        SetterUtil.setIfPresent(map, "flow_id", req::setFlowId);
        SetterUtil.setIfPresent(map, "From", req::setFrom);
        SetterUtil.setIfPresent(map, "To", req::setTo);
        SetterUtil.setIfPresent(map, "CurrentTime", req::setCurrentTime);
        SetterUtil.setIfPresent(map, "DialCallStatus", req::setDialCallStatus);
        SetterUtil.setIfPresent(map, "CustomField", req::setCustomField);
        SetterUtil.setIfPresent(map, "RecordingUrl", req::setRecordingUrl);

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
