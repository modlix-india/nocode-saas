package com.fincity.saas.message.model.request.call.provider.telecmi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import java.util.Map;

/**
 * One TeleCMI webhook, event or CDR, read from raw JSON because TeleCMI names and types fields differently
 * between the two ({@code extra_params} has arrived as an object, a JSON string and {@code {"{}": null}}).
 * Accessors answer null rather than throw. {@code call_id} is not read: it differs between a leg's events and CDR.
 */
public record TelecmiWebhook(JsonNode body) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String TYPE_CDR = "cdr";

    public static TelecmiWebhook of(JsonNode body) {
        return new TelecmiWebhook(body == null ? MAPPER.createObjectNode() : body);
    }

    private String text(String field) {
        JsonNode value = this.body.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    public String type() {
        return this.text("type");
    }

    public boolean isCdr() {
        return TYPE_CDR.equalsIgnoreCase(this.type());
    }

    /** {@code a} or {@code b}; absent on an inbound call's caller leg and on its CDR. */
    public String leg() {
        return this.text("leg");
    }

    public String status() {
        return this.text("status");
    }

    public String cmiuuid() {
        return this.text("cmiuuid");
    }

    public String requestId() {
        return this.text("request_id");
    }

    /** On every webhook of an inbound call: the HTTP flow's {@code cmiuuid}. */
    public String conversationUuid() {
        return this.text("conversation_uuid");
    }

    /** {@code app_id} on events, {@code appid} on CDRs. */
    public String appId() {
        String appId = this.text("app_id");
        return appId != null ? appId : this.text("appid");
    }

    /** The leg's start in epoch millis; the same on all of its webhooks. */
    public Long time() {
        JsonNode value = this.body.get("time");
        return value != null && value.canConvertToLong() ? value.asLong() : null;
    }

    /** Talk time on a CDR; absent on a missed call. */
    public long answeredSeconds() {
        JsonNode value = this.body.get("answeredsec");
        return value != null && value.canConvertToLong() ? value.asLong() : 0L;
    }

    public String hangupReason() {
        return this.text("hangup_reason");
    }

    /** The recording's file name, only when the CDR says the call was recorded. */
    public String recordingFile() {
        JsonNode record = this.body.get("record");
        return record != null && record.asBoolean(false) ? this.text("filename") : null;
    }

    /** Our row's code, echoed from the click-to-call's {@code extra_params}. */
    public String callCode() {
        JsonNode params = this.body.get("extra_params");
        if (params == null || params.isNull()) return null;

        if (params.isTextual()) {
            try {
                params = MAPPER.readTree(params.asText());
            } catch (Exception unreadable) {
                return null;
            }
        }

        JsonNode code = params == null ? null : params.get(TelecmiApiConfig.EXTRA_CALL_CODE);
        return code == null || !code.isTextual() || code.asText().isBlank()
                ? null
                : code.asText().trim();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap() {
        return MAPPER.convertValue(this.body, Map.class);
    }
}
