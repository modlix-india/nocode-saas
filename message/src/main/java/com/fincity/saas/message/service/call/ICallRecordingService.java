package com.fincity.saas.message.service.call;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Providers whose call recordings are played through this service. Provider recording URLs carry the account's
 * credentials, so the browser is only ever handed our URL, keyed on our own call code.
 */
public interface ICallRecordingService {

    /**
     * Where every recording is played from, with our call code appended. Relative, as every softphone URL is: the
     * page's {@code /<app>/<client>/page} prefix then carries the tenant, which an absolute {@code /api/...} drops
     * on hosts that address the app by path.
     */
    String RECORDING_URI = "api/message/call/recording/";

    /** The operation a "could not reach the provider" error names. */
    String OPERATION_PLAY = "recording download";

    ConnectionSubType getConnectionSubType();

    /**
     * Streams one call's recording within the caller's app and client. Empty when the code is not this provider's
     * call, so {@link CallService} asks the next; this provider's call without a recording is a not-found error.
     */
    Mono<ResponseEntity<Flux<DataBuffer>>> recording(MessageAccess access, String callCode, String range);

    /** Our URL for a call's recording, or null when the provider has reported none. */
    static String recordingUri(String callCode, boolean hasRecording) {
        return hasRecording && callCode != null ? RECORDING_URI + callCode : null;
    }

    static <T> Mono<T> unavailable(MessageResourceService msgService, String callCode) {
        return msgService.throwMessage(
                msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                MessageResourceService.CALL_RECORDING_NOT_AVAILABLE,
                callCode);
    }

    /**
     * Fetches a recording from its provider and streams it back. {@code Range} is forwarded and a partial answer
     * passed back. An error status, or anything not audio (a redirect, which is not followed, or TeleCMI's refusal
     * in a JSON body), is not available, its body drained. No answer at all is {@code unreachable}, which must not
     * name the URL: the provider's can carry its credentials.
     */
    static Mono<ResponseEntity<Flux<DataBuffer>>> stream(
            Mono<WebClient> client,
            Function<WebClient, WebClient.RequestHeadersSpec<?>> request,
            String range,
            MessageResourceService msgService,
            String callCode,
            Supplier<Mono<ResponseEntity<Flux<DataBuffer>>>> unreachable) {

        return client.flatMap(webClient -> request.apply(webClient)
                        .headers(headers -> {
                            if (!StringUtil.safeIsBlank(range)) headers.set(HttpHeaders.RANGE, range);
                        })
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.releaseBody()
                                .then(ICallRecordingService.<Throwable>unavailable(msgService, callCode)))
                        .toEntityFlux(DataBuffer.class))
                .flatMap(entity -> isAudio(entity.getHeaders().getContentType())
                        ? Mono.just(asPlayable(entity))
                        : entity.getBody()
                                .doOnNext(DataBufferUtils::release)
                                .then(ICallRecordingService.<ResponseEntity<Flux<DataBuffer>>>unavailable(
                                        msgService, callCode)))
                .onErrorResume(e -> !(e instanceof GenericException), e -> unreachable.get());
    }

    static boolean isAudio(MediaType type) {
        return type != null && "audio".equalsIgnoreCase(type.getType());
    }

    /** The provider's status and the headers a player needs, and nothing else of the provider's. */
    static ResponseEntity<Flux<DataBuffer>> asPlayable(ResponseEntity<Flux<DataBuffer>> entity) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(entity.getHeaders().getContentType());
        for (String name : List.of(HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_RANGE, HttpHeaders.ACCEPT_RANGES)) {
            String value = entity.getHeaders().getFirst(name);
            if (value != null) headers.set(name, value);
        }
        // A recording of a customer's call: never kept by a shared cache.
        headers.setCacheControl(CacheControl.noStore().cachePrivate());

        return ResponseEntity.status(entity.getStatusCode()).headers(headers).body(entity.getBody());
    }
}
