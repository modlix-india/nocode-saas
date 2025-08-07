package com.fincity.saas.message.service.call.provider.exotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.enums.call.provider.exotel.option.ExotelDirection;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallStatusCallback;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelConnectAppletRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelPassThruCallback;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelCallResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelErrorResponse;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.SetterUtil;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO> {

    public static final String EXOTEL_PROVIDER_URI = "/exotel";
    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    @Override
    protected String getCacheName() {
        return EXOTEL_CALL_CACHE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.EXOTEL;
    }

    @Override
    public String getProviderUri() {
        return EXOTEL_PROVIDER_URI;
    }

    @Override
    public Mono<Call> toCall(ExotelCall providerObject) {
        return Mono.just(new Call()
                        .setUserId(providerObject.getUserId())
                        .setFromDialCode(providerObject.getFromDialCode())
                        .setFrom(providerObject.getFrom())
                        .setToDialCode(providerObject.getToDialCode())
                        .setTo(providerObject.getTo())
                        .setCallerId(providerObject.getCallerId())
                        .setCallProvider(this.getConnectionSubType().getProvider())
                        .setIsOutbound(ExotelDirection.getByName(providerObject.getDirection())
                                .isOutbound())
                        .setStatus(providerObject.getExotelCallStatus().toCallStatus())
                        .setStartTime(providerObject.getStartTime())
                        .setEndTime(providerObject.getEndTime())
                        .setDuration(providerObject.getDuration())
                        .setRecordingUrl(providerObject.getRecordingUrl())
                        .setExotelCallId(providerObject.getId() != null ? providerObject.getId() : null)
                        .setMetadata(providerObject.toMap()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.toCall"));
    }

    @Override
    public Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection) {
        String to = callRequest.getToNumber().getNumber();
        String callerId = callRequest.getCallerId();

        if (to == null) return super.throwMissingParam(ExotelCallRequest.Fields.to);

        if (callerId == null) return super.throwMissingParam(ExotelCallRequest.Fields.callerId);

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(to, callerId, Boolean.TRUE);
        this.applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.makeExotelCall(access, exotelCallRequest, connection),
                        (vConn, exotelCall) -> this.createInternal(access, exotelCall),
                        (vConn, exotelCall, created) ->
                                this.toCall(exotelCall).map(call -> call.setConnectionName(connection.getName())),
                        (vConn, exotelCall, eCreated, cCreated) -> super.callEventService
                                .sendMakeCallEvent(
                                        access.getAppCode(), access.getClientCode(), access.getUserId(), eCreated)
                                .thenReturn(cCreated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall"));
    }

    private void applyConnectionDetailsToRequest(ExotelCallRequest request, Map<String, Object> details) {
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.callType, String.class),
                request::setCallType);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.timeLimit, Integer.class),
                request::setTimeLimit);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.timeOut, Integer.class),
                request::setTimeOut);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.waitUrl, String.class),
                request::setWaitUrl);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.doRecord, Boolean.class),
                request::setDoRecord);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.recordingChannels, String.class),
                request::setRecordingChannels);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.recordingFormat, String.class),
                request::setRecordingFormat);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallback, String.class),
                request::setStatusCallback);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallbackEvents, String[].class),
                request::setStatusCallbackEvents);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.statusCallbackContentType, String.class),
                request::setStatusCallbackContentType);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelCallRequest.Fields.customField, String.class),
                request::setCustomField);
    }

    private Mono<ExotelCall> makeExotelCall(MessageAccess messageAccess, ExotelCallRequest request, Connection conn) {

        request.setFrom(
                PhoneUtil.parse(messageAccess.getUser().getPhoneNumber()).getNumber());

        return FlatMapUtil.flatMapMono(
                        () -> super.getCallBackUrl(conn.getAppCode(), conn.getClientCode()),
                        callBackUri -> request.setStatusCallback(callBackUri).toFormDataAsync(),
                        (callBackUri, formData) -> webClientConfig.createExotelWebClient(conn),
                        (callBackUri, formData, webClient) -> {
                            logger.debug("Exotel API Request FormData: {}", formData);

                            return webClient
                                    .post()
                                    .uri(ExotelApiConfig.getCallUrl())
                                    .contentType(MediaType.MULTIPART_FORM_DATA)
                                    .bodyValue(formData)
                                    .retrieve()
                                    .onStatus(
                                            status -> status.is4xxClientError() || status.is5xxServerError(),
                                            clientResponse -> clientResponse
                                                    .bodyToMono(ExotelErrorResponse.class)
                                                    .flatMap(errorBody -> {
                                                        logger.error(
                                                                "Error response received from Exotel: {}", errorBody);
                                                        return this.msgService.throwStrMessage(
                                                                msg -> new GenericException(
                                                                        HttpStatus.resolve(
                                                                                errorBody
                                                                                        .getRestException()
                                                                                        .getStatus()),
                                                                        msg),
                                                                errorBody
                                                                        .getRestException()
                                                                        .getMessage());
                                                    }))
                                    .bodyToMono(ExotelCallResponse.class)
                                    .map(response -> new ExotelCall(request)
                                            .setUserId(messageAccess.getUserId())
                                            .update(response));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeExotelCall"));
    }

    public Mono<ExotelConnectAppletResponse> connectCall(IncomingCallRequest incomingCallRequest) {

        if (incomingCallRequest.getUserId() == null) {
            logger.error("Missing required parameter: UserId");
            return Mono.empty();
        }

        Map<String, Object> providerRequest = incomingCallRequest.getProviderIncomingRequest();

        if (providerRequest == null || providerRequest.isEmpty()) {
            logger.error("Missing required parameter: provider information");
            return Mono.empty();
        }

        return FlatMapUtil.flatMapMono(
                        super::hasPublicAccess,
                        publicAccess -> super.getUserIdAndPhone(incomingCallRequest.getUserId()),
                        (publicAccess, user) -> super.callConnectionService.getConnection(
                                publicAccess.getAppCode(),
                                publicAccess.getClientCode(),
                                incomingCallRequest.getConnectionName()),
                        (publicAccess, user, connection) -> this.createExotelCall(
                                providerRequest, user.getValue().getNumber()),
                        (publicAccess, user, connection, exotelCall) -> {
                            if (connection.getConnectionType() != ConnectionType.CALL
                                    || connection.getConnectionSubType() != ConnectionSubType.EXOTEL) {
                                logger.error(
                                        "Invalid connection type: Expected CALL/CALL_EXOTEL but got {}/{}",
                                        connection.getConnectionType(),
                                        connection.getConnectionSubType());
                                return Mono.empty();
                            }

                            Mono<ExotelCall> exotelCreated = this.createInternal(publicAccess, exotelCall);

                            Mono<Call> callCreated = this.toCall(exotelCall)
                                    .map(call -> call.setConnectionName(connection.getName()))
                                    .flatMap(call -> super.callService.createInternal(
                                            publicAccess, incomingCallRequest.getUserId(), call));

                            Mono<ExotelConnectAppletResponse> responseCreated =
                                    createResponse(user.getValue().getNumber(), connection);

                            return Mono.zip(exotelCreated, callCreated, responseCreated)
                                    .flatMap(tuple -> super.callEventService
                                            .sendIncomingCallEvent(
                                                    publicAccess.getAppCode(),
                                                    publicAccess.getClientCode(),
                                                    user.getId(),
                                                    tuple.getT1())
                                            .thenReturn(tuple.getT3()));
                        })
                .cast(ExotelConnectAppletResponse.class)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.connectCall"));
    }

    private Mono<ExotelCall> createExotelCall(Map<String, Object> providerRequest, String destinationNumber) {
        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(providerRequest);
        ExotelCall exotelCall = new ExotelCall(exotelRequest);

        if (exotelCall.getDirection() == null) exotelCall.setDirection(ExotelDirection.INBOUND.name());

        exotelCall.setRecordingUrl(exotelRequest.getRecordingUrl());
        exotelCall.setTo(destinationNumber);
        return Mono.just(exotelCall);
    }

    private Mono<ExotelConnectAppletResponse> createResponse(String destination, Connection connection) {
        ExotelConnectAppletResponse response = new ExotelConnectAppletResponse();

        this.applyConnectionDetailsToResponse(response, connection.getConnectionDetails());
        response.setDestination(new ExotelConnectAppletResponse.Destination().setNumbers(List.of(destination)));

        return Mono.just(response);
    }

    private void applyConnectionDetailsToResponse(ExotelConnectAppletResponse response, Map<String, Object> details) {
        response.setFetchAfterAttempt(super.getConnectionDetail(
                details, ExotelConnectAppletResponse.Fields.fetchAfterAttempt, Boolean.class));
        response.setDoRecord(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.doRecord, Boolean.class));
        response.setMaxRingingDuration(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.maxRingingDuration, Long.class));
        response.setMaxConversationDuration(super.getConnectionDetail(
                details, ExotelConnectAppletResponse.Fields.maxConversationDuration, Long.class));

        ExotelConnectAppletResponse.ParallelRinging parallelRinging = new ExotelConnectAppletResponse.ParallelRinging();
        parallelRinging.setActivate(super.getConnectionDetail(
                details, ExotelConnectAppletResponse.ParallelRinging.Fields.activate, Boolean.class));
        parallelRinging.setMaxParallelAttempts(super.getConnectionDetail(
                details, ExotelConnectAppletResponse.ParallelRinging.Fields.maxParallelAttempts, Integer.class));

        response.setParallelRinging(parallelRinging);
    }

    public Mono<ExotelCall> processCallStatusCallback(ExotelCallStatusCallback callback) {
        if (callback.getCallSid() == null) {
            logger.error("CallerSid not provided in Exotel CallBack. Discarding...");
            return Mono.empty();
        }

        return FlatMapUtil.flatMapMono(
                        () -> this.findByUniqueField(callback.getCallSid()),
                        exotelCall -> this.update(exotelCall.update(callback)),
                        (exotelCall, updated) -> super.callEventService
                                .sendCallStatusEvent(
                                        updated.getAppCode(), updated.getClientCode(), updated.getUserId(), updated)
                                .thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processCallStatusCallback"))
                .switchIfEmpty(Mono.defer(() -> {
                    logger.error("Exotel CallSid not found. Discarding...");
                    return Mono.empty();
                }))
                .onErrorResume(e -> {
                    logger.error("Error processing Exotel Call Status callback", e);
                    return Mono.empty();
                });
    }

    public Mono<ExotelCall> processPassThruCallback(ExotelPassThruCallback callback) {
        if (callback.getCallSid() == null) {
            logger.error("CallSid not provided in Exotel Passthru Callback. Discarding...");
            return Mono.empty();
        }

        return FlatMapUtil.flatMapMono(
                        () -> this.findByUniqueField(callback.getCallSid()),
                        exotelCall -> this.update(exotelCall.update(callback)),
                        (exotelCall, updated) -> super.callEventService
                                .sendPassthruCallbackEvent(
                                        updated.getAppCode(), updated.getClientCode(), updated.getUserId(), updated)
                                .thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processPassThruCallback"))
                .switchIfEmpty(Mono.defer(() -> {
                    logger.error("Exotel CallSid not found. Discarding...");
                    return Mono.empty();
                }))
                .onErrorResume(e -> {
                    logger.error("Error processing Exotel Passthru callback", e);
                    return Mono.empty();
                });
    }
}
