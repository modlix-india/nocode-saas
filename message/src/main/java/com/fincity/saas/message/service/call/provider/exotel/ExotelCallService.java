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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ExotelCallService extends AbstractCallProviderService<MessageExotelCallsRecord, ExotelCall, ExotelDAO> {

    private static final Logger logger = LoggerFactory.getLogger(ExotelCallService.class);

    public static final String EXOTEL_PROVIDER_URI = "/exotel";
    private static final String EXOTEL_CALL_CACHE = "exotelCall";

    @Value("${app.exotel.connect.default.outgoing-phone-number:}")
    private String defaultOutgoingPhoneNumber;

    @Value("${app.exotel.connect.default.record:false}")
    private boolean defaultRecord;

    @Value("${app.exotel.connect.default.recording-channels:single}")
    private String defaultRecordingChannels;

    @Value("${app.exotel.connect.default.max-ringing-duration:30}")
    private int defaultMaxRingingDuration;

    @Value("${app.exotel.connect.default.max-conversation-duration:900}")
    private int defaultMaxConversationDuration;

    @Value("${app.exotel.connect.default.music-on-hold.type:default_tone}")
    private String defaultMusicOnHoldType;

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

    public Mono<ExotelConnectAppletResponse> processConnectAppletRequest(ExotelConnectAppletRequest request) {
        logger.debug("Processing Connect applet request: {}", request);

        return FlatMapUtil.flatMapMono(
                        () -> {
                            if (request.getCallSid() != null) {
                                return this.findByUniqueField(request.getCallSid())
                                        .switchIfEmpty(Mono.defer(() -> Mono.just(new ExotelCall(request))));
                            } else {
                                return Mono.just(new ExotelCall(request));
                            }
                        },
                        exotelCall -> exotelCall.getId() != null ? this.update(exotelCall) : this.create(exotelCall),
                        (exotelCall, savedCall) -> {
                            List<String> destinationNumbers = getDestinationNumbers(request);

                            ExotelConnectAppletResponse response = ExotelConnectAppletResponse.builder()
                                    .fetchAfterAttempt(false)
                                    .destinationNumbers(destinationNumbers)
                                    .outgoingPhoneNumber(
                                            defaultOutgoingPhoneNumber.isEmpty() ? null : defaultOutgoingPhoneNumber)
                                    .record(defaultRecord)
                                    .recordingChannels(defaultRecordingChannels)
                                    .maxRingingDuration(defaultMaxRingingDuration)
                                    .maxConversationDuration(defaultMaxConversationDuration)
                                    .musicOnHold(defaultMusicOnHoldType, null)
                                    .build();

                            logger.debug("Generated Connect applet response: {}", response);
                            return Mono.just(response);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ExotelCallService.processConnectAppletRequest"))
                .onErrorResume(e -> {
                    logger.error("Error processing Connect applet request", e);
                    // Return a default response in case of error
                    return Mono.just(createDefaultResponse());
                });
    }

    private List<String> getDestinationNumbers(ExotelConnectAppletRequest request) {
        // This is a placeholder implementation.
        // In a real application, you would determine the destination numbers based on the request.
        // For example, you might look up the agent numbers based on the caller's number,
        // the time of day, the IVR selection (digits), etc.

        // For now, we'll just return a hardcoded number
        return Arrays.asList("+919876543210");
    }

    private ExotelConnectAppletResponse createDefaultResponse() {
        return ExotelConnectAppletResponse.builder()
                .fetchAfterAttempt(false)
                .destinationNumbers(Arrays.asList("+919876543210"))
                .record(defaultRecord)
                .recordingChannels(defaultRecordingChannels)
                .maxRingingDuration(defaultMaxRingingDuration)
                .maxConversationDuration(defaultMaxConversationDuration)
                .musicOnHold(defaultMusicOnHoldType, null)
                .build();
    }
}
