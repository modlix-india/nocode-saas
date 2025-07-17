package com.fincity.saas.message.model.request.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.springframework.util.MultiValueMap;

@Data
@Accessors(chain = true)
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelConnectAppletRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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

    // Optional parameters that may be present depending on conditions
    @JsonProperty("DialCallStatus")
    private String dialCallStatus;

    @JsonProperty("digits")
    private String digits;

    @JsonProperty("CustomField")
    private String customField;

    @JsonProperty("RecordingUrl")
    private String recordingUrl;

    /**
     * Creates an ExotelConnectAppletRequest from query parameters.
     *
     * @param queryParams The query parameters from the HTTP request
     * @return The created ExotelConnectAppletRequest
     */
    public static ExotelConnectAppletRequest fromQueryParams(MultiValueMap<String, String> queryParams) {
        ExotelConnectAppletRequest request = new ExotelConnectAppletRequest();

        if (queryParams.containsKey("CallSid")) request.setCallSid(queryParams.getFirst("CallSid"));
        if (queryParams.containsKey("CallFrom")) request.setCallFrom(queryParams.getFirst("CallFrom"));
        if (queryParams.containsKey("CallTo")) request.setCallTo(queryParams.getFirst("CallTo"));
        if (queryParams.containsKey("Direction")) request.setDirection(queryParams.getFirst("Direction"));
        if (queryParams.containsKey("Created")) request.setCreated(queryParams.getFirst("Created"));

        if (queryParams.containsKey("DialCallDuration")) {
            try {
                request.setDialCallDuration(Long.parseLong(queryParams.getFirst("DialCallDuration")));
            } catch (NumberFormatException e) {
            }
        }

        if (queryParams.containsKey("StartTime")) request.setStartTime(queryParams.getFirst("StartTime"));
        if (queryParams.containsKey("EndTime")) request.setEndTime(queryParams.getFirst("EndTime"));
        if (queryParams.containsKey("CallType")) request.setCallType(queryParams.getFirst("CallType"));
        if (queryParams.containsKey("DialWhomNumber"))
            request.setDialWhomNumber(queryParams.getFirst("DialWhomNumber"));
        if (queryParams.containsKey("flow_id")) request.setFlowId(queryParams.getFirst("flow_id"));
        if (queryParams.containsKey("From")) request.setFrom(queryParams.getFirst("From"));
        if (queryParams.containsKey("To")) request.setTo(queryParams.getFirst("To"));
        if (queryParams.containsKey("CurrentTime")) request.setCurrentTime(queryParams.getFirst("CurrentTime"));

        if (queryParams.containsKey("DialCallStatus"))
            request.setDialCallStatus(queryParams.getFirst("DialCallStatus"));

        if (queryParams.containsKey("digits")) {
            String digits = queryParams.getFirst("digits");
            if (digits != null) {
                digits = digits.trim();
                if (digits.startsWith("\"") && digits.endsWith("\"")) {
                    digits = digits.substring(1, digits.length() - 1);
                }
                request.setDigits(digits);
            }
        }

        if (queryParams.containsKey("CustomField")) request.setCustomField(queryParams.getFirst("CustomField"));
        if (queryParams.containsKey("RecordingUrl")) request.setRecordingUrl(queryParams.getFirst("RecordingUrl"));

        return request;
    }
}
