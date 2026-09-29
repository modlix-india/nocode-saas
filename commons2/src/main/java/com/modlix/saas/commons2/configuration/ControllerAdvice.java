package com.modlix.saas.commons2.configuration;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import com.modlix.saas.commons2.exception.GenericException;

import feign.FeignException;
import jakarta.annotation.Priority;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@Priority(0)
public class ControllerAdvice {

    @Autowired
    private AbstractMessageService resourceService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

    @ExceptionHandler(GenericException.class)
    public ResponseEntity<Object> handleGenericException(GenericException ex) {
        logger.debug("GenericException Occurred : ", ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.toExceptionData());
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Object> handleFeignException(FeignException fe) {
        logger.debug("FeignException Occurred : ", fe);
        return handleFeignExceptionInternal(fe);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOtherExceptions(Exception ex) {
        logger.debug("Exception Occurred : ", ex);
        return handleOtherExceptionsInternal(ex);
    }

    /**
     * True when the exception says the CLIENT went away, not that this service failed.
     *
     * <p>Spring raises {@link AsyncRequestNotUsableException} ("Servlet container error
     * notification for disconnected client") when it cannot write a response because the socket
     * is gone. On an SSE endpoint that is not an edge case, it is the normal way a stream ends:
     * every browser tab closed on notification's {@code /subscribe} produces one. On production,
     * 2026-09-29, it produced 848 ERROR lines with full stack traces in a day - by itself more
     * than every other service's errors combined, all of it describing users closing tabs.
     *
     * <p>It cannot be caught by status, because it is not a {@link ResponseStatusException} and
     * so defaults to 500. Note also that nothing can be written to the response at this point,
     * so the status chosen below is only ever for the log.
     */
    private static boolean isClientDisconnect(Throwable ex) {
        for (Throwable t = ex; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof AsyncRequestNotUsableException)
                return true;
            // Tomcat's ClientAbortException is not on the compile classpath here and arrives
            // wrapped in several different types depending on where the write failed, so it is
            // matched by name rather than by instanceof.
            String name = t.getClass().getName();
            if (name.endsWith("ClientAbortException") || name.endsWith("AsyncRequestTimeoutException"))
                return true;
        }
        return false;
    }

    private ResponseEntity<Object> handleOtherExceptionsInternal(Exception ex) {
        String eId = GenericException.uniqueId();
        String msg = resourceService.getMessage(AbstractMessageService.UNKNOWN_ERROR_WITH_ID, eId);

        final HttpStatus status = (ex instanceof ResponseStatusException rse)
                ? HttpStatus.valueOf(rse.getStatusCode().value())
                : HttpStatus.INTERNAL_SERVER_ERROR;

        // The status was already computed just below, and the ERROR log sat above it, so every
        // exception was a server fault with a stack trace - including the ones that only say
        // something about the request or the client. 96% of the estate's ERROR volume was this
        // kind of noise, and it is why worker's genuine nightly 401 went unnoticed for weeks at
        // one line a day. Both quieter paths keep the stack at DEBUG, so /actuator/loggers can
        // turn a specific one back on without a restart.
        if (isClientDisconnect(ex)) {
            logger.debug("Client disconnected before the response could be written : {}", eId, ex);
        } else if (status.is4xxClientError()) {
            logger.debug("Client error {} : {}", status.value(), eId, ex);
        } else {
            logger.error("Error : {}", eId, ex);
        }

        GenericException g = new GenericException(status, eId, msg, ex);
        return ResponseEntity.status(g.getStatusCode())
                .body(g.toExceptionData());
    }

    private ResponseEntity<Object> handleFeignExceptionInternal(FeignException fe) {
        Optional<ByteBuffer> byteBuffer = fe.responseBody();
        if (byteBuffer.isPresent() && byteBuffer.get()
                .hasArray()) {

            Collection<String> ctype = fe.responseHeaders()
                    .get(HttpHeaders.CONTENT_TYPE);
            if (ctype != null && ctype.contains("application/json")) {
                try {
                    Map<String, Object> map = this.objectMapper.readValue(byteBuffer.get()
                            .array(), new TypeReference<Map<String, Object>>() {
                            });
                    GenericException g = new GenericException(HttpStatus.valueOf(fe.status()),
                            map.get("message") == null ? ""
                                    : map.get("message")
                                            .toString(),
                            fe);
                    return ResponseEntity.status(g.getStatusCode())
                            .body(g.toExceptionData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Fallback to generic error handling
        return handleOtherExceptionsInternal(fe);
    }

}
