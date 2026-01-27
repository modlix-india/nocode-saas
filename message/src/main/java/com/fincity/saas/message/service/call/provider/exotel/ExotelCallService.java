package com.fincity.saas.message.service.call.provider.exotel;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.configuration.call.exotel.ExotelApiConfig;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.enums.MessageSeries;
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
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import com.fincity.saas.message.util.PhoneUtil;
import com.fincity.saas.message.util.SetterUtil;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO> {

    public static final String EXOTEL_PROVIDER_URI = "/exotel";
    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.EXOTEL_CALL;
    }

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
    protected Mono<ExotelCall> updatableEntity(ExotelCall entity) {
        return super.updatableEntity(entity).flatMap(existing -> {

            existing.setParentCallSid(entity.getParentCallSid());
            existing.setDateCreated(entity.getDateCreated());
            existing.setDateUpdated(entity.getDateUpdated());

            existing.setExotelCallStatus(entity.getExotelCallStatus());
            existing.setEndTime(entity.getEndTime());
            existing.setDuration(entity.getDuration());
            existing.setPrice(entity.getPrice());
            existing.setDirection(entity.getDirection());
            existing.setAnsweredBy(entity.getAnsweredBy());
            existing.setRecordingUrl(entity.getRecordingUrl());
            existing.setConversationDuration(entity.getConversationDuration());
            existing.setLeg1Status(entity.getLeg1Status());
            existing.setLeg2Status(entity.getLeg2Status());
            existing.setLegs(entity.getLegs());
            existing.setExotelCallResponse(entity.getExotelCallResponse());

            return Mono.just(existing);
        });
    }

    @Override
    public Mono<Call> toCall(ExotelCall providerObject) {
        return Mono.just(new Call()
                        .setUserId(providerObject.getUserId())
                        .setCallProvider(this.getConnectionSubType().getProvider())
                        .setIsOutbound(ExotelDirection.getByName(providerObject.getDirection())
                                .isOutbound())
                        .setExotelCallId(providerObject.getId() != null ? providerObject.getId() : null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.toCall"));
    }

    @Override
    public Mono<Call> makeCall(MessageAccess access, CallRequest callRequest, Connection connection) {
        String to = callRequest.getToNumber().getNumber();

        String callerId = callRequest.getCallerId() == null
                ? (String) connection.getConnectionDetails().get(ExotelCallRequest.Fields.callerId)
                : callRequest.getCallerId().getLandlineNumber();

        if (to == null) return super.throwMissingParam(ExotelCallRequest.Fields.to);

        if (callerId == null) return super.throwMissingParam(ExotelCallRequest.Fields.callerId);

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(to, callerId, Boolean.TRUE);
        this.applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.makeExotelCall(access, exotelCallRequest, connection),
                        (vConn, eCreated) ->
                                this.toCall(eCreated).map(call -> call.setConnectionName(connection.getName())),
                        (vConn, eCreated, call) -> super.callService.createInternal(access, call),
                        (vConn, eCreated, call, cCall) -> super.callEventService
                                .sendMakeCallEvent(
                                        access.getAppCode(), access.getClientCode(), access.getUserId(), eCreated)
                                .thenReturn(cCall))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall"));
    }

    public Mono<Call> makeCall(CallRequest callRequest) {

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.callConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), callRequest.getConnectionName()),
                        (access, connection) -> super.isValidConnection(connection),
                        (access, connection, vConn) -> this.makeCall(access, callRequest, connection))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall(CallRequest)"));
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
                        () -> super.getCallBackAppUrl(conn.getAppCode()),
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
                                    .bodyToMono(ExotelCallResponse.class);
                        },
                        (callBackUri, formData, webClient, response) -> this.createInternal(
                                messageAccess, ExotelCall.ofOutbound(request).update(response)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeExotelCall"));
    }

    public Mono<ExotelConnectAppletResponse> connectCall(
            String appCode, String clientCode, IncomingCallRequest request) {

        if (request.getUserId() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "userId");

        Map<String, String> providerRequest = request.getProviderIncomingRequest();

        if (providerRequest == null || providerRequest.isEmpty())
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "providerIncomingRequest");

        ExotelConnectAppletRequest exotelRequest = ExotelConnectAppletRequest.of(providerRequest);

        if (exotelRequest.getCallSid() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "CallSid");

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);

        return FlatMapUtil.flatMapMono(
                        () -> this.existsByUniqueField(access, exotelRequest.getCallSid())
                                .flatMap(BooleanUtil::safeValueOfFalseWithEmpty)
                                .switchIfEmpty(super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        MessageResourceService.DUPLICATE_CALL_SID,
                                        exotelRequest.getCallSid())),
                        notExists -> super.callConnectionService.getCoreDocument(
                                access.getAppCode(), access.getClientCode(), request.getConnectionName()),
                        (notExists, connection) -> super.getUserIdAndPhone(request.getUserId()),
                        (notExists, connection, user) ->
                                Mono.just(ExotelCall.ofInbound(exotelRequest, user.getValue(), (String)
                                        connection.getConnectionDetails().getOrDefault("accountSid", ""))),
                        (notExists, connection, user, exotelCall) -> {
                            Mono<ExotelCall> exotelCreated = this.createInternal(access, user.getId(), exotelCall);

                            Mono<Call> callCreated = this.toCall(exotelCall)
                                    .map(call -> call.setConnectionName(connection.getName()))
                                    .flatMap(call -> super.callService.createInternal(access, user.getId(), call));

                            Mono<ExotelConnectAppletResponse> responseCreated =
                                    createResponse(user.getValue().getNumber(), connection);

                            return Mono.zip(exotelCreated, callCreated, responseCreated)
                                    .<ExotelConnectAppletResponse>flatMap(tuple -> super.callEventService
                                            .sendIncomingCallEvent(
                                                    access.getAppCode(),
                                                    access.getClientCode(),
                                                    user.getId(),
                                                    tuple.getT1())
                                            .thenReturn(tuple.getT3()));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.connectCall"));
    }

    private Mono<ExotelConnectAppletResponse> createResponse(String destination, Connection connection) {
        ExotelConnectAppletResponse response = new ExotelConnectAppletResponse();

        this.applyConnectionDetailsToResponse(response, connection.getConnectionDetails());
        response.setDestination(new ExotelConnectAppletResponse.Destination().setNumbers(List.of(destination)));

        return Mono.just(response);
    }

    private void applyConnectionDetailsToResponse(ExotelConnectAppletResponse response, Map<String, Object> details) {
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.fetchAfterAttempt, Boolean.class),
                response::setFetchAfterAttempt);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.doRecord, Boolean.class),
                response::setDoRecord);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(details, ExotelConnectAppletResponse.Fields.maxRingingDuration, Long.class),
                response::setMaxRingingDuration);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.Fields.maxConversationDuration, Long.class),
                response::setMaxConversationDuration);

        ExotelConnectAppletResponse.ParallelRinging parallelRinging = new ExotelConnectAppletResponse.ParallelRinging();

        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.ParallelRinging.Fields.activate, Boolean.class),
                parallelRinging::setActivate);
        SetterUtil.setIfPresent(
                super.getConnectionDetail(
                        details, ExotelConnectAppletResponse.ParallelRinging.Fields.maxParallelAttempts, Integer.class),
                parallelRinging::setMaxParallelAttempts);

        response.setParallelRinging(parallelRinging);
    }

    public Mono<ExotelCall> processCallStatusCallback(MessageAccess access, ExotelCallStatusCallback callback) {

        if (callback.getCallSid() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "CallSid");

        logger.info("Processing call status callback for callSid: {}", callback.getCallSid());

        return FlatMapUtil.flatMapMono(
                        () -> this.findByUniqueField(access, callback.getCallSid())
                                .switchIfEmpty(super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        MessageResourceService.CALL_NOT_FOUND,
                                        callback.getCallSid())),
                        exotelCall -> super.updateInternalWithoutUser(
                                MessageAccess.of(exotelCall.getAppCode(), exotelCall.getClientCode(), true),
                                exotelCall.update(callback)),
                        (exotelCall, updated) -> super.callEventService
                                .sendCallStatusEvent(
                                        updated.getAppCode(), updated.getClientCode(), updated.getUserId(), updated)
                                .thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processCallStatusCallback"));
    }

    public Mono<ExotelCall> processPassThruCallback(MessageAccess access, ExotelPassThruCallback callback) {

        if (callback.getCallSid() == null)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.MISSING_CALL_PARAMETERS,
                    this.getConnectionSubType().getProvider(),
                    "CallSid");

        logger.info("Processing pass-thru callback for callSid: {}", callback.getCallSid());

        return FlatMapUtil.flatMapMono(
                        () -> this.findByUniqueField(access, callback.getCallSid())
                                .switchIfEmpty(super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        MessageResourceService.CALL_NOT_FOUND,
                                        callback.getCallSid())),
                        exotelCall -> super.updateInternalWithoutUser(
                                MessageAccess.of(exotelCall.getAppCode(), exotelCall.getClientCode(), true),
                                exotelCall.update(callback)),
                        (exotelCall, updated) -> super.callEventService
                                .sendPassthruCallbackEvent(
                                        updated.getAppCode(), updated.getClientCode(), updated.getUserId(), updated)
                                .thenReturn(updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processPassThruCallback"));
    }
}
