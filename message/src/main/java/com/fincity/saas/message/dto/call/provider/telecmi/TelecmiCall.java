package com.fincity.saas.message.dto.call.provider.telecmi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * One TeleCMI call, the twin of {@code ExotelCall}. Leg 1 is the agent and leg 2 the customer in both
 * directions; TeleCMI's statuses are kept as sent, and {@link #callStatus} is derived from leg 2.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class TelecmiCall extends BaseUpdatableDto<TelecmiCall> {

    @Serial
    private static final long serialVersionUID = 2918463750183644192L;

    /** {@code request_id} outbound, the HTTP-flow {@code cmiuuid} inbound; null until TeleCMI answers a placement. */
    private String providerCallId;

    private String connectionName;

    private String ownerService;

    /** Ours, never TeleCMI's: its inbound agent leg says outbound. */
    private Boolean isOutbound = Boolean.TRUE;

    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;

    private Integer customerDialCode = PhoneUtil.getDefaultCallingCode();
    private String customerPhoneNumber;

    private String callerId;
    private CallStatus callStatus = CallStatus.QUEUED;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private Long conversationDuration;

    /** Played through our proxy only: {@code /v2/play} carries the secret in its query string. */
    private String recordingFile;

    private String leg1Cmiuuid;
    private String leg1Status;
    private String leg1HangupReason;
    private String leg2Cmiuuid;
    private String leg2Status;
    private String leg2HangupReason;

    /** Never with the app secret in it. */
    @ToString.Exclude
    private Map<String, Object> telecmiCallRequest;

    @ToString.Exclude
    private Map<String, Object> telecmiCallResponse;

    @ToString.Exclude
    private Map<String, Object> telecmiHttpFlowRequest;

    @ToString.Exclude
    private Map<String, Object> leg1Cdr;

    /** On an inbound call, the only CDR. */
    @ToString.Exclude
    private Map<String, Object> leg2Cdr;

    public TelecmiCall() {
        super();
    }

    /** Derived, not stored; entity-processor reads it to stamp its own row. */
    @JsonProperty(value = "callProvider", access = JsonProperty.Access.READ_ONLY)
    public String getCallProvider() {
        return ConnectionSubType.TELECMI.name();
    }

    /** Written before our HTTP flow reply tells TeleCMI whom to ring. */
    public static TelecmiCall ofInbound(
            String cmiuuid, String connectionName, PhoneNumber caller, PhoneNumber did, Map<String, Object> request) {

        TelecmiCall call = new TelecmiCall()
                .setProviderCallId(cmiuuid)
                .setConnectionName(connectionName)
                .setIsOutbound(Boolean.FALSE)
                .setCallStatus(CallStatus.QUEUED)
                .setStartTime(LocalDateTime.now())
                .setTelecmiHttpFlowRequest(request);

        if (caller != null)
            call.setFromDialCode(caller.getCountryCode())
                    .setFrom(caller.getNumber())
                    .setCustomerDialCode(caller.getCountryCode())
                    .setCustomerPhoneNumber(caller.getNumber());

        if (did != null)
            call.setToDialCode(did.getCountryCode()).setTo(did.getNumber()).setCallerId(did.getNumber());

        return call;
    }
}
