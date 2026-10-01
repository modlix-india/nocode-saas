package com.fincity.saas.message.dto.call.provider.telecmi;

import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiWebhook;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * What one webhook may change on its call's row, decided without reading the row. TeleCMI sends each leg's
 * events and CDR separately, out of order, so {@code TelecmiDAO.apply} writes this as guarded column updates.
 *
 * <p>Leg 1 is the agent, leg 2 the customer. Outbound, TeleCMI's {@code a} is the agent and {@code b} the
 * customer; inbound, the caller's leg has no {@code leg}, the agent's is {@code b}, and the one CDR is the caller's.
 *
 * @param onlyIfCustomerNeverRang the outcome applies only while the customer leg has not started
 */
public record TelecmiWebhookEffect(
        int leg,
        String cmiuuid,
        String legStatus,
        boolean cdr,
        String hangupReason,
        Map<String, Object> cdrBody,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean progressed,
        CallStatus finalStatus,
        boolean onlyIfCustomerNeverRang,
        Long conversationDuration,
        String recordingFile,
        String providerCallId) {

    public static final int NO_LEG = 0;
    public static final int AGENT_LEG = 1;
    public static final int CUSTOMER_LEG = 2;

    /** Set only by a CDR. Not {@code FAILED}: a call placement marked failed that rang anyway must still report. */
    public static final Set<CallStatus> FINAL =
            Set.of(CallStatus.COMPLETE, CallStatus.BUSY, CallStatus.NO_ANSWER, CallStatus.UNKNOWN);

    private static final String STATUS_STARTED = "started";
    private static final String STATUS_ANSWERED = "answered";
    private static final String STATUS_MISSED = "missed";
    private static final String STATUS_HANGUP = "hangup";

    /** The only hangup reason that changes the outcome. */
    public static final String REASON_REJECTED = "sent_reject";

    public static TelecmiWebhookEffect of(TelecmiCall call, TelecmiWebhook webhook, LocalDateTime arrivedAt) {

        int leg = legOf(call, webhook);
        boolean cdr = webhook.isCdr();
        String status = webhook.status();

        boolean customer = leg == CUSTOMER_LEG;
        boolean ended = customer && (cdr || STATUS_HANGUP.equalsIgnoreCase(status));
        boolean progressed =
                !cdr && (STATUS_STARTED.equalsIgnoreCase(status) || STATUS_ANSWERED.equalsIgnoreCase(status));

        CallStatus finalStatus = null;
        boolean onlyIfCustomerNeverRang = false;

        if (cdr && customer) finalStatus = outcome(status, webhook.hangupReason());
        else if (cdr && leg == AGENT_LEG && STATUS_MISSED.equalsIgnoreCase(status)) {
            finalStatus = CallStatus.NO_ANSWER;
            onlyIfCustomerNeverRang = true;
        }

        return new TelecmiWebhookEffect(
                leg,
                legId(call, webhook, leg),
                leg == NO_LEG ? null : status,
                cdr,
                cdr ? webhook.hangupReason() : null,
                cdr && leg != NO_LEG ? webhook.asMap() : null,
                webhook.time() == null
                        ? null
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(webhook.time()), ZoneId.systemDefault()),
                ended || onlyIfCustomerNeverRang ? arrivedAt : null,
                progressed,
                finalStatus,
                onlyIfCustomerNeverRang,
                cdr && customer ? webhook.answeredSeconds() : null,
                cdr ? webhook.recordingFile() : null,
                webhook.requestId());
    }

    /** Inbound, the customer leg's id is the conversation's: the one CDR carries the agent's {@code cmiuuid}. */
    static String legId(TelecmiCall call, TelecmiWebhook webhook, int leg) {
        if (leg == CUSTOMER_LEG && Boolean.FALSE.equals(call.getIsOutbound()) && webhook.conversationUuid() != null)
            return webhook.conversationUuid();
        return webhook.cmiuuid();
    }

    static int legOf(TelecmiCall call, TelecmiWebhook webhook) {

        String leg = webhook.leg();

        if (!Boolean.FALSE.equals(call.getIsOutbound())) {
            if ("a".equalsIgnoreCase(leg)) return AGENT_LEG;
            if ("b".equalsIgnoreCase(leg)) return CUSTOMER_LEG;
            return NO_LEG;
        }

        if ("b".equalsIgnoreCase(leg)) return AGENT_LEG;
        if (leg == null) return CUSTOMER_LEG;
        return NO_LEG;
    }

    static CallStatus outcome(String status, String hangupReason) {
        if (STATUS_ANSWERED.equalsIgnoreCase(status)) return CallStatus.COMPLETE;
        if (STATUS_MISSED.equalsIgnoreCase(status))
            return REASON_REJECTED.equalsIgnoreCase(hangupReason) ? CallStatus.BUSY : CallStatus.NO_ANSWER;
        return CallStatus.UNKNOWN;
    }
}
