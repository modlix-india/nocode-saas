package com.fincity.saas.message.service.call.provider.exotel;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.dao.call.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelDirection;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO> {

    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    private URI createExotelUrl(String apiKey, String apiToken, String subDomain, String sid) {
        String schema = "https";
        String userInfo = apiKey + ":" + apiToken;
        String path = "/v1/Accounts/" + sid + "/Calls/connect";

        try {
            return new URI(schema, userInfo, subDomain, -1, path, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getCacheName() {
        return EXOTEL_CALL_CACHE;
    }

    @Override
    public String getProvider() {
        return ConnectionSubType.CALL_EXOTEL.getProvider();
    }

    @Override
    public Mono<Call> toCall(ExotelCall providerObject) {
        Call call = new Call()
                .setFrom(providerObject.getFrom())
                .setFromDialCode(providerObject.getFromDialCode())
                .setTo(providerObject.getTo())
                .setToDialCode(providerObject.getToDialCode())
                .setCallerId(providerObject.getCallerId())
                .setCallProvider(this.getProvider())
                .setIsOutbound(
                        ExotelDirection.getByName(providerObject.getDirection()).isOutbound())
                .setStartTime(providerObject.getStartTime())
                .setEndTime(providerObject.getEndTime())
                .setDuration(providerObject.getDuration())
                .setRecordingUrl(providerObject.getRecordingUrl());

        if (providerObject.getExotelCallStatus() != null) {
            switch (providerObject.getExotelCallStatus()) {
                case COMPLETED -> call.setStatus(CallStatus.COMPLETE);
                case FAILED -> call.setStatus(CallStatus.FAILED);
                case BUSY -> call.setStatus(CallStatus.BUSY);
                case NO_ANSWER -> call.setStatus(CallStatus.NO_ANSWER);
                case CANCELED -> call.setStatus(CallStatus.CANCELED);
                default -> call.setStatus(CallStatus.UNKNOWN);
            }
        } else {
            call.setStatus(CallStatus.ORIGINATE);
        }

        if (providerObject.getId() != null) call.setExotelCallId(providerObject.getId());

        return Mono.just(call);
    }

    @Override
    public Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection) {

        if (connection.getConnectionType() != ConnectionType.CALL
                || connection.getConnectionSubType() != ConnectionSubType.CALL_EXOTEL)
            return this.getMsgService()
                    .throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.INVALID_CONNECTION_TYPE);

        String from = callRequest.getFromNumber().getNumber();
        String to = callRequest.getToNumber().getNumber();
        String callerId = callRequest.getCallerId();

        if (from == null) return super.throwMissingParam("from");

        if (to == null) return super.throwMissingParam("to");

        if (callerId == null) return super.throwMissingParam("callerId");

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(from, to, callerId, true);
        applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());

        return makeExotelCall(exotelCallRequest, connection).flatMap(this::toCall);
    }

    private void applyConnectionDetailsToRequest(ExotelCallRequest request, Map<String, Object> details) {
        request.setCallType(super.getConnectionDetail(details, ExotelCallRequest.Fields.callType, String.class));
        request.setTimeLimit(super.getConnectionDetail(details, ExotelCallRequest.Fields.timeLimit, Integer.class));
        request.setTimeOut(super.getConnectionDetail(details, ExotelCallRequest.Fields.timeOut, Integer.class));
        request.setWaitUrl(super.getConnectionDetail(details, ExotelCallRequest.Fields.waitUrl, String.class));
        request.setDoRecord(super.getConnectionDetail(details, ExotelCallRequest.Fields.doRecord, Boolean.class));
        request.setRecordingChannels(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.recordingChannels, String.class));
        request.setRecordingFormat(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.recordingFormat, String.class));
        request.setStatusCallback(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallback, String.class));
        request.setStatusCallbackEvents(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallbackEvents, String[].class));
        request.setStatusCallbackContentType(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallbackContentType, String.class));
        request.setCustomField(super.getConnectionDetail(details, ExotelCallRequest.Fields.customField, String.class));
    }

    private Mono<ExotelCall> makeExotelCall(ExotelCallRequest request, Connection conn) {
        Map<String, Object> details = conn.getConnectionDetails();

        return Mono.zip(
                        super.getRequiredConnectionDetail(details, "apiKey"),
                        super.getRequiredConnectionDetail(details, "apiToken"),
                        super.getRequiredConnectionDetail(details, "accountSid"),
                        super.getRequiredConnectionDetail(details, "subdomain"))
                .flatMap(tuple -> {
                    URI uri = this.createExotelUrl(tuple.getT1(), tuple.getT2(), tuple.getT4(), tuple.getT3());

                    return request.toFormDataAsync().flatMap(formData -> WebClient.create()
                            .post()
                            .uri(uri)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters.fromFormData(formData))
                            .retrieve()
                            .bodyToMono(ExotelCallResponse.class)
                            .map(response -> new ExotelCall(request).update(response)));
                });
    }
}
