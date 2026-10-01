package com.fincity.saas.message.service.call;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Providers whose call recordings are played through this service. Provider recording URLs carry the account's
 * credentials, so the browser is only ever handed our URL, keyed on our own call code.
 */
public interface ICallRecordingService {

    /** Where every recording is played from, with our call code appended. */
    String RECORDING_URI = "/api/message/call/recording/";

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
