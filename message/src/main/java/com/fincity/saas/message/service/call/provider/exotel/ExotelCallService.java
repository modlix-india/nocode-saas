package com.fincity.saas.message.service.call.provider.exotel;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
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
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.provider.AbstractCallProviderService;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple3;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO> {

    public static final String EXOTEL_PROVIDER_URI = "/exotel";
    private static final Logger logger = LoggerFactory.getLogger(ExotelCallService.class);
    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    private Mono<URI> createExotelUrl(String apiKey, String apiToken, String subDomain, String sid) {
        String schema = "https";
        String userInfo = apiKey + ":" + apiToken;
        String path = "/v1/Accounts/" + sid + "/Calls/connect";

        try {
            return Mono.just(new URI(schema, userInfo, subDomain, -1, path, null, null));
        } catch (URISyntaxException e) {
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.URL_CREATION_ERROR,
                    this.getProvider());
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
    public String getProviderUri() {
        return EXOTEL_PROVIDER_URI;
    }

    @Override
    public Mono<Call> toCall(ExotelCall providerObject) {
        return Mono.just(new Call()
                        .setFromDialCode(providerObject.getFromDialCode())
                        .setFrom(providerObject.getFrom())
                        .setToDialCode(providerObject.getToDialCode())
                        .setTo(providerObject.getTo())
                        .setCallerId(providerObject.getCallerId())
                        .setCallProvider(this.getProvider())
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

        ExotelCallRequest exotelCallRequest = ExotelCallRequest.of(from, to, callerId, Boolean.TRUE);
        this.applyConnectionDetailsToRequest(exotelCallRequest, connection.getConnectionDetails());

        return FlatMapUtil.flatMapMono(
                        () -> this.makeExotelCall(exotelCallRequest, connection),
                        exotelCall -> this.createInternal(access, exotelCall),
                        (exotelCall, created) ->
                                this.toCall(exotelCall).map(call -> call.setConnectionName(connection.getName())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeCall"));
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

        return FlatMapUtil.flatMapMono(
                        () -> super.getCallBackUrl(conn.getAppCode(), conn.getClientCode()),
                        callBackUri -> Mono.zip(
                                super.getRequiredConnectionDetail(details, "apiKey"),
                                super.getRequiredConnectionDetail(details, "apiToken"),
                                super.getRequiredConnectionDetail(details, "subdomain"),
                                super.getRequiredConnectionDetail(details, "accountSid")),
                        (callBackUri, requiredParam) -> this.createExotelUrl(
                                requiredParam.getT1(),
                                requiredParam.getT2(),
                                requiredParam.getT3(),
                                requiredParam.getT4()),
                        (callBackUri, requiredParam, uri) ->
                                request.setStatusCallback(callBackUri).toFormDataAsync(),
                        (callBackUri, requiredParam, uri, formData) -> WebClient.create()
                                .post()
                                .uri(uri)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(BodyInserters.fromFormData(formData))
                                .retrieve()
                                .bodyToMono(ExotelCallResponse.class)
                                .map(response -> new ExotelCall(request).update(response)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.makeExotelCall"));
    }

    public Mono<ExotelConnectAppletResponse> connectCall(
            IncomingCallRequest incomingCallRequest, Connection connection) {
        if (connection.getConnectionType() != ConnectionType.CALL
                || connection.getConnectionSubType() != ConnectionSubType.CALL_EXOTEL)
            return this.getMsgService()
                    .throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            MessageResourceService.INVALID_CONNECTION_TYPE);

        if (incomingCallRequest.getDestination() == null
                || incomingCallRequest.getDestination().isEmpty()) return super.throwMissingParam("destination");

        Map<String, Object> providerRequest = incomingCallRequest.getProviderIncomingRequest();

        if (providerRequest == null || providerRequest.isEmpty())
            return super.throwMissingParam("provider information");

        //TODO: Right now only supporting single destination number
        String destinationNumber =
                incomingCallRequest.getDestination().getFirst().getNumber();

        return FlatMapUtil.flatMapMono(
                        super::hasPublicAccess,
                        publicAccess -> this.createExotelCall(providerRequest, destinationNumber),
                        (publicAccess, exotelCall) -> {
                            Mono<ExotelCall> exotelCreated = createInternal(publicAccess, exotelCall);

                            Mono<Call> callCreated = toCall(exotelCall)
                                    .map(call -> call.setConnectionName(connection.getName()))
                                    .flatMap(call -> super.callService.createInternal(publicAccess, call));

                            Mono<ExotelConnectAppletResponse> responseCreated =
                                    createResponse(destinationNumber, connection);

                            return Mono.zip(exotelCreated, callCreated, responseCreated)
                                    .map(Tuple3::getT3);
                        })
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
                        exotelCall -> this.update(exotelCall.update(callback)))
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
                        () -> this.findByUniqueField(callback.getCallSid()).switchIfEmpty(Mono.defer(() -> {
                            ExotelCall newCall = new ExotelCall();
                            newCall.setSid(callback.getCallSid());
                            return Mono.just(newCall);
                        })),
                        exotelCall -> {
                            exotelCall.update(callback);
                            return exotelCall.getId() != null ? this.update(exotelCall) : this.create(exotelCall);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processPassThruCallback"))
                .onErrorResume(e -> {
                    logger.error("Error processing Exotel Passthru callback", e);
                    return Mono.empty();
                });
    }
}
